package pl.michalbzowski.windband.application.command.composition;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;
import pl.michalbzowski.windband.application.command.composition.UploadValidator.UploadRejectedException;
import pl.michalbzowski.windband.application.dto.composition.ScoreFileDto;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.composition.ScoreFileThumbQueryService;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;

/**
 * US-7.15 — replace the CONTENT of an already-uploaded score file in place, without
 * touching its identity. The librarian edited the piece (bars changed), re-exported the
 * part/score PDF, and wants the new bytes behind the SAME row so every
 * {@code composition_instruments.score_file_id} mapping keeps pointing at it — the
 * mapping is a statement about which pages hold which voice, and an edited engraving
 * normally keeps that layout; only the pixels change.
 *
 * <p>Because the row survives, the read paths (download, thumbnails, Epic 6 part links,
 * share tokens) need no repair: they resolve through the same id and simply start serving
 * the new bytes. What a content swap DOES invalidate is recorded here, in one place:</p>
 * <ol>
 *   <li><b>Part audit pairs</b> — a frozen {@code verifiedBy/verifiedAt} pair certifies
 *       "these pages of THIS file hold this voice". The certification was about the old
 *       bytes, so parts bound to the replaced file, plus every legacy part the read path
 *       currently resolves to this file (the "newest file whose pageCount covers pageTo"
 *       rule mirrored from {@code PartLinkQueryService}), get their pair cleared via
 *       {@link CompositionInstrument#invalidateVerification()} and must be re-verified
 *       through the US-3.03 gate.</li>
 *   <li><b>Composition status</b> — a READY ("Gotowy") piece claimed its whole map was
 *       human-approved for the old score; that claim dies with the swap, so the
 *       composition returns to DRAFT ("Szkic") via {@link Composition#markDraft()}.
 *       ARCHIVED rows stay ARCHIVED (markDraft is a READY-only demotion).</li>
 *   <li><b>Thumbnail cache</b> — cached JPEGs render the OLD pages, so
 *       {@link ScoreFileThumbQueryService#evictFile(Long)} drops every cached view of the
 *       file before the response goes out.</li>
 * </ol>
 *
 * <p><b>Guards before a single byte is written:</b> same band/composition ownership checks
 * as the delete path (400 unknown band, 409 foreign file), MIME whitelist (415), size cap
 * (413), same-MIME requirement (422 — a ZIP row can never be replaced in place because its
 * extracted children would keep serving stale entries; replace the children instead), and
 * the new page count must still cover every explicitly bound part range (422 listing the
 * blocking voices) so the DB never ends up holding a mapping the file's own
 * {@code pageCount} contradicts — the invariant {@code CompositionInstrument.forComposition}
 * enforces at write time.</p>
 *
 * <p>This class is in the application layer and does NOT reference any Spring Web type
 * (ArchitectureTest). The old on-disk bytes are removed best-effort AFTER the row was
 * updated in this transaction — a failed deletion only orphans a file, mirroring
 * {@link ScoreFileDeleteCommandService}'s trade-off.</p>
 */
@Service
@Slf4j
public class ScoreFileReplaceCommandService {

    private final BandQueryService bandQueryService;
    private final CompositionRepository compositionRepository;
    private final ScoreFileRepository scoreFileRepository;
    private final CompositionInstrumentRepository compositionInstrumentRepository;
    private final UploadValidator uploadValidator;
    private final ScoreFileStorage storage;
    private final PdfPageCounter pdfPageCounter;
    private final ScoreFileThumbQueryService thumbQueryService;

    public ScoreFileReplaceCommandService(BandQueryService bandQueryService,
                                          CompositionRepository compositionRepository,
                                          ScoreFileRepository scoreFileRepository,
                                          CompositionInstrumentRepository compositionInstrumentRepository,
                                          UploadValidator uploadValidator,
                                          ScoreFileStorage storage,
                                          PdfPageCounter pdfPageCounter,
                                          ScoreFileThumbQueryService thumbQueryService) {
        this.bandQueryService               = Objects.requireNonNull(bandQueryService, "bandQueryService");
        this.compositionRepository          = Objects.requireNonNull(compositionRepository, "compositionRepository");
        this.scoreFileRepository            = Objects.requireNonNull(scoreFileRepository, "scoreFileRepository");
        this.compositionInstrumentRepository = Objects.requireNonNull(
                compositionInstrumentRepository, "compositionInstrumentRepository");
        this.uploadValidator                = Objects.requireNonNull(uploadValidator, "uploadValidator");
        this.storage                        = Objects.requireNonNull(storage, "storage");
        this.pdfPageCounter                 = Objects.requireNonNull(pdfPageCounter, "pdfPageCounter");
        this.thumbQueryService              = Objects.requireNonNull(thumbQueryService, "thumbQueryService");
    }

    /**
     * Swaps the bytes behind {@code fileId} for the ones in {@code request}, preserving the
     * row's identity (and therefore every part mapping), clearing the stale verification
     * audit, demoting a READY composition to DRAFT and evicting cached thumbnails.
     *
     * @return the updated row as {@link ScoreFileDto} (new name/size/sha/pageCount, same id)
     */
    @Transactional
    public ScoreFileDto replace(ScoreFileUploadRequest request, Long fileId, Long compositionId, Long bandId) {
        if (request == null || request.bytes() == null || request.bytes().length == 0) {
            throw new UploadRejectedException(400, "Brak treści pliku do zastąpienia.");
        }
        // Layer 1 — band must exist (IllegalArgumentException → 400 via GlobalExceptionHandler).
        bandQueryService.getRequiredBand(bandId);

        // Layer 2 — the file row exists and is the composition's own file (IllegalStateException → 409).
        ScoreFile file = scoreFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalStateException("Score file " + fileId + " does not exist"));
        if (file.getComposition() == null || !Objects.equals(file.getComposition().getId(), compositionId)) {
            throw new IllegalStateException(
                    "Score file " + fileId + " is not part of composition " + compositionId);
        }
        // Layer 3 — multi-tenant gate: the composition itself must resolve inside this band.
        Composition composition = compositionRepository.findByIdAndBandId(compositionId, bandId)
                .orElseThrow(() -> new IllegalStateException(
                        "Composition " + compositionId + " does not belong to band " + bandId));

        // A ZIP row is only a container — replacing it in place would leave every extracted
        // child (and any mapping bound to a child) describing the OLD archive. Fail closed.
        if ("application/zip".equalsIgnoreCase(file.getMimeType())) {
            throw new UploadRejectedException(422,
                    "Nie można wymienić archiwum ZIP w miejscu. Wymień pojedyncze pliki wyciągnięte "
                            + "z archiwum (przycisk „Wymień” przy głosie) albo usuń archiwum i wgraj nowe.");
        }

        // Type + size validation before any byte is written: same MIME whitelist as upload,
        // the new file must keep the row's declared type, and the size cap is re-applied.
        String contentType = request.contentType();
        uploadValidator.requireAllowedMime(contentType);
        if (contentType == null || !contentType.equalsIgnoreCase(file.getMimeType())) {
            throw new UploadRejectedException(422,
                    "Nowy plik musi mieć ten sam typ co zastępowany (" + file.getMimeType() + ").");
        }
        uploadValidator.requireAllowedSize(request.size(), contentType);

        Integer newPageCount = "application/pdf".equalsIgnoreCase(contentType)
                ? pdfPageCounter.extract(request.bytes())
                : null;

        List<CompositionInstrument> parts = compositionInstrumentRepository.findAllByComposition(composition);

        // Mapping integrity: every explicitly bound part must stay inside the new PDF's pages.
        // Reject BEFORE touching disk — the librarian edits the mapping (or picks a fatter file)
        // first; we never leave the DB asserting pages the file does not have.
        if (newPageCount != null) {
            List<String> blockers = parts.stream()
                    .filter(p -> boundTo(p, fileId))
                    .filter(p -> p.getPageTo() != null && p.getPageTo() > newPageCount)
                    .map(p -> "„" + p.getInstrumentRole() + "” (strony " + p.getPageFrom() + "–" + p.getPageTo() + ")")
                    .toList();
            if (!blockers.isEmpty()) {
                throw new UploadRejectedException(422,
                        "Nowy plik ma " + newPageCount + " stron, a głosy są zmapowane poza ten zakres: "
                                + String.join(", ", blockers)
                                + ". Zmień mapowanie głosów albo wgraj plik o wystarczającej liczbie stron.");
            }
        }

        // Which legacy (unbound) parts currently resolve to THIS file? Their delivered content
        // is about to change, so their audit pair must fall too. Mirror PartLinkQueryService.open:
        // newest id first, first file with pageCount >= pageTo wins.
        List<ScoreFile> filesBeforeSwap = scoreFileRepository.findAllByComposition(composition);
        Comparator<ScoreFile> byIdDesc = Comparator.comparing(ScoreFile::getId, Comparator.reverseOrder());

        ScoreFileStorage.StoredFile stored;
        String oldStoragePath = file.getStoragePath();
        try {
            stored = storage.store(new ByteArrayInputStream(request.bytes()), request.size(),
                    request.originalFileName());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("Nie udało się zapisać nowego pliku na dysku.", e);
        }

        file.replaceContent(request.originalFileName(), request.size(),
                stored.sha256(), stored.storagePath(), newPageCount);
        scoreFileRepository.save(file);

        int invalidated = 0;
        for (CompositionInstrument part : parts) {
            boolean touchesThisFile = boundTo(part, fileId) || (part.getScoreFile() == null && resolvesTo(part, file, filesBeforeSwap, byIdDesc));
            if (touchesThisFile && (part.getVerifiedAt() != null || part.getVerifiedBy() != null)) {
                part.invalidateVerification();
                compositionInstrumentRepository.save(part);
                invalidated++;
            }
        }

        // "Gotowy" was a claim about the OLD notation — the edited score returns it to "Szkic".
        boolean wasReady = composition.getStatus()
                == pl.michalbzowski.windband.domain.composition.CompositionStatus.READY;
        composition.markDraft();
        compositionRepository.save(composition);

        // Cached thumbnails show the pre-swap pages.
        thumbQueryService.evictFile(fileId);

        physicallyDeleteQuietly(oldStoragePath);

        log.info("Replaced score file id={} (compositionId={}, bandId={}) — {} pages, "
                        + "{} part(s) invalidated for re-verification, readyToDraft={}",
                fileId, compositionId, bandId, newPageCount, invalidated, wasReady);

        return new ScoreFileDto(
                file.getId(),
                compositionId,
                file.getOriginalName(),
                file.getSizeBytes(),
                file.getMimeType(),
                file.getPageCount(),
                false,
                file.getSha256(),
                file.getParentFileId(),
                Instant.now());
    }

    private static boolean boundTo(CompositionInstrument part, Long fileId) {
        return part.getScoreFile() != null && Objects.equals(part.getScoreFile().getId(), fileId);
    }

    /** Legacy resolution rule: this file is the one the read path would pick for the part? */
    private static boolean resolvesTo(CompositionInstrument part, ScoreFile file,
                                      List<ScoreFile> filesBeforeSwap, Comparator<ScoreFile> byIdDesc) {
        if (part.getPageTo() == null) {
            return false;
        }
        return filesBeforeSwap.stream()
                .filter(f -> f.getId() != null)
                .sorted(byIdDesc)
                .filter(f -> f.getPageCount() != null && f.getPageCount() >= part.getPageTo())
                .findFirst()
                .map(f -> Objects.equals(f.getId(), file.getId()))
                .orElse(false);
    }

    /** Best-effort removal of the superseded bytes — mirrors the delete service's trade-off. */
    private void physicallyDeleteQuietly(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(storagePath));
        } catch (IOException e) {
            log.warn("Failed to delete superseded on-disk score file at {} — new bytes are live, "
                    + "the orphaned old copy needs an ops sweep", storagePath, e);
        }
    }
}
