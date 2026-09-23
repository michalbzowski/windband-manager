package pl.michalbzowski.windband.application.query.composition;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;

import java.util.List;
import java.util.Objects;

/**
 * US-7.10 — single read path that backs both the part "link" GET and the "share by email" send:
 * <ul>
 *   <li>resolves the {@code CompositionInstrument} row in a way that survives cross-band probing
 *       (the band must match on BOTH sides);</li>
 *   <li>picks the composition's "largest" uploaded score file whose pageCount covers the part's
 *       page range, so the share/preview link streams a file that actually has those pages; and</li>
 *   <li>returns the scalar fields (id, name, mime, size, pageFrom/pageTo) that the adapter layer
 *       needs for headers — never exposing any eager-loaded association through to a template.</li>
 * </ul>
 */
@Service
public class PartLinkQueryService {

    private final BandQueryService bandQueryService;
    private final CompositionInstrumentRepository compositionInstrumentRepository;
    private final ScoreFileRepository scoreFileRepository;

    public PartLinkQueryService(BandQueryService bandQueryService,
                                CompositionInstrumentRepository compositionInstrumentRepository,
                                ScoreFileRepository scoreFileRepository) {
        this.bandQueryService                = bandQueryService;
        this.compositionInstrumentRepository = Objects.requireNonNull(compositionInstrumentRepository);
        this.scoreFileRepository             = Objects.requireNonNull(scoreFileRepository);
    }

    /** Result: flat scalars only (no domain objects), suitable for template rendering. */
    public record PartLink(
            long partId,
            long compositionId,
            long bandId,
            long fileId,
            String originalName,      // e.g. "score.pdf" — used when no dedicated part file exists
            String mimeType,          // normally "application/pdf"
            long sizeBytes,
            String compositionTitle,  // human-readable for the email subject + link label
            String roleText,          // e.g. "Flet 1" (or "—")
            int pageFrom,             // inclusive, 1-based
            int pageTo                // inclusive
    ) {}

    /** Raised when no uploaded score file covers the requested page range — mapped to HTTP 409. */
    public static class NoCoveringFileException extends IllegalStateException {
        public NoCoveringFileException(String message) { super(message); }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public PartLink open(long partId, long compositionId, long bandId) {
        // Layer 1 — the band must exist (IllegalArgumentException → 400 via GlobalExceptionHandler).
        bandQueryService.getRequiredBand(bandId);

        // Layer 2 — the part exists and its composition matches the URL's.
        var part = compositionInstrumentRepository.findById(partId)
                .orElseThrow(() -> new IllegalStateException("Głos " + partId + " nie istnieje."));
        long partCompositionId = part.getComposition().getId();
        if (partCompositionId != compositionId) {
            throw new IllegalStateException("Głos " + partId + " nie należy do utworu " + compositionId);
        }
        Long partBand = part.getComposition().getBand() == null
                ? null : part.getComposition().getBand().getId();
        if (partBand == null || partBand != bandId) {
            throw new IllegalStateException("Głos " + partId + " nie należy do zespołu " + bandId);
        }

        // Layer 3 — pick a file that actually has the part's pages. Newest matching file wins; if two
        // have the same pageCount we still prefer the higher id (the later upload). If none covers
        // pageTo, throw NoCoveringFileException → 409.
        List<ScoreFile> candidates = scoreFileRepository.findAllByComposition(part.getComposition());
        java.util.Comparator<ScoreFile> byIdDesc = (a, b) -> Long.compare(b.getId(), a.getId());
        ScoreFile best = candidates.stream()
                .filter(f -> f.getId() != null)
                .sorted(byIdDesc)
                .filter(f -> f.getPageCount() != null && f.getPageCount() >= part.getPageTo())
                .findFirst()
                .orElseThrow(() -> new NoCoveringFileException(
                        "Żaden wgrany plik nut nie obejmuje stron " + part.getPageFrom() + "–" + part.getPageTo()
                                + " utworu o id=" + compositionId));

        return new PartLink(
                part.getId(),
                partCompositionId,
                bandId,
                best.getId(),
                Objects.requireNonNullElse(best.getOriginalName(), "scorefile-" + best.getId()),
                Objects.requireNonNullElse(best.getMimeType(), "application/pdf"),
                Objects.requireNonNullElse(best.getSizeBytes(), 0L),
                part.getComposition().getTitle(),
                (part.getInstrumentRole() == null || part.getInstrumentRole().isBlank()) ? "głos" : part.getInstrumentRole(),
                part.getPageFrom(),
                part.getPageTo());
    }

    /**
     * US-7.11 — same ownership + covering-file validation as {@link #open} but throws the result
     * away; the token endpoints use it so a share link is never minted for a part that would
     * immediately 404/409 when opened.
     *
     * <p>Carries the SAME transactional boundary as {@link #open}: the self-invocation below
     * bypasses the proxy, so without its own {@code @Transactional} the lazy
     * {@code composition.band} chain would explode with LazyInitializationException outside
     * any session (caught by the Selenium regression in CompositionDetailPartsUiTest).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void requireOpenable(long partId, long compositionId, long bandId) {
        open(partId, compositionId, bandId);
    }
}
