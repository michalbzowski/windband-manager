package pl.michalbzowski.windband.application.command.composition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import pl.michalbzowski.windband.application.command.composition.UploadValidator.UploadRejectedException;
import pl.michalbzowski.windband.application.dto.composition.ScoreFileDto;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.composition.ScoreFileThumbQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.PartSource;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;
import pl.michalbzowski.windband.domain.member.Instrument;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * US-7.15 — the replace command service in unit form: identity of the row (and therefore every
 * mapping) survives, while the guards (ZIP parent, MIME equality, shrinking page count below a
 * bound mapping, cross-band writes) fail closed BEFORE any byte is stored, and the stale state
 * (verification audit pairs, READY status, cached thumbnails, old on-disk copy) is cleared only
 * on the happy path.
 *
 * <p>Real domain objects are used for the state transitions (markDraft / invalidateVerification /
 * replaceContent carry the business rules), with ids injected via ReflectionTestUtils — mirroring
 * what a persisted row looks like. Mocks cover the repository/storage seams only.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScoreFileReplaceCommandServiceTest {

    @Mock private BandQueryService bandQueryService;
    @Mock private CompositionRepository compositionRepository;
    @Mock private ScoreFileRepository scoreFileRepository;
    @Mock private CompositionInstrumentRepository instrumentRepository;
    @Mock private ScoreFileStorage storage;
    @Mock private PdfPageCounter pdfPageCounter;
    @Mock private ScoreFileThumbQueryService thumbQueryService;

    private ScoreFileReplaceCommandService service;

    private Band band;
    private Composition composition;
    private Path tmpRoot;

    private static final Long BAND_ID   = 1L;
    private static final Long COMP_ID   = 100L;
    private static final Long FILE_ID   = 20L;
    private static final String SHA_NEW = "bb".repeat(32);

    @BeforeEach
    void setUp() throws IOException {
        service = new ScoreFileReplaceCommandService(bandQueryService, compositionRepository,
                scoreFileRepository, instrumentRepository, new UploadValidator(null),
                storage, pdfPageCounter, thumbQueryService);

        band = Band.create("Test Band", "test-band");
        ReflectionTestUtils.setField(band, "id", BAND_ID);
        composition = Composition.create("Marsz", "opis", "kompozytor", null, band);
        ReflectionTestUtils.setField(composition, "id", COMP_ID);

        tmpRoot = Files.createTempDirectory("sf-replace-unit-");

        when(bandQueryService.getRequiredBand(BAND_ID)).thenReturn(band);
        when(compositionRepository.findByIdAndBandId(COMP_ID, BAND_ID)).thenReturn(Optional.of(composition));
        when(compositionRepository.save(any(Composition.class))).thenAnswer(i -> i.getArgument(0));
        when(instrumentRepository.save(any(CompositionInstrument.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    // ---- fixtures -------------------------------------------------------------

    private ScoreFile file(Long id, String mime, String name, Integer pageCount) {
        ScoreFile f = ScoreFile.forComposition(composition, mime, name, 100L,
                "aa".repeat(32), "/somewhere/" + name, pageCount);
        ReflectionTestUtils.setField(f, "id", id);
        return f;
    }

    private Path realFileOnDisk(ScoreFile f) throws IOException {
        Path p = tmpRoot.resolve("stored-" + f.getId() + ".bin");
        Files.writeString(p, "OLD-BYTES");
        ReflectionTestUtils.setField(f, "storagePath", p.toAbsolutePath().toString());
        return p;
    }

    private Instrument instrument() {
        Instrument i = Instrument.create("Trąbka Bb", band);
        ReflectionTestUtils.setField(i, "id", 5L);
        return i;
    }

    private CompositionInstrument part(String role, int from, int to, ScoreFile bound) {
        CompositionInstrument p = (bound == null)
                ? CompositionInstrument.forComposition(composition, instrument(), role, from, to, null, PartSource.MANUAL, 1.0)
                : CompositionInstrument.forComposition(composition, instrument(), role, from, to, null, bound, PartSource.MANUAL, 1.0);
        ReflectionTestUtils.setField(p, "id", (long) role.hashCode());
        return p;
    }

    private ScoreFileUploadRequest newPdfRequest() {
        return new ScoreFileUploadRequest("marsz_v2.pdf", "application/pdf", new byte[]{1, 2, 3});
    }

    private void stubHappyStorageAndPages(int newPageCount) throws IOException {
        when(pdfPageCounter.extract(any())).thenReturn(newPageCount);
        when(storage.store(any(InputStream.class), anyLong(), org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(new ScoreFileStorage.StoredFile(
                        tmpRoot.resolve("new-bytes.pdf").toAbsolutePath().toString(), SHA_NEW, 3L));
    }

    // ---- happy path -------------------------------------------------------------

    @Test
    @DisplayName("replace() swaps content IN PLACE: same row id, new metadata, mappings intact")
    void replace_swapsContent_rowIdentityPreserved() throws IOException {
        ScoreFile target = ScoreFile.forComposition(composition, "application/pdf", "marsz.pdf",
                100L, "aa".repeat(32), "/somewhere/marsz.pdf", 2, 999L); // ZIP-extracted child
        ReflectionTestUtils.setField(target, "id", FILE_ID);
        Path oldBytes = realFileOnDisk(target);
        ScoreFile newerCovering = file(30L, "application/pdf", "calosc.pdf", 12);
        realFileOnDisk(newerCovering);

        when(scoreFileRepository.findById(FILE_ID)).thenReturn(Optional.of(target));
        when(scoreFileRepository.findAllByComposition(composition))
                .thenReturn(List.of(newerCovering, target));
        when(instrumentRepository.findAllByComposition(composition)).thenReturn(List.of());
        stubHappyStorageAndPages(3);

        ScoreFileDto dto = service.replace(newPdfRequest(), FILE_ID, COMP_ID, BAND_ID);

        // Identity preserved — this is what keeps every composition_instruments FK valid.
        assertThat(dto.fileId()).isEqualTo(FILE_ID);
        assertThat(target.getId()).isEqualTo(FILE_ID);
        assertThat(target.getMimeType()).isEqualTo("application/pdf");
        assertThat(target.getOriginalName()).isEqualTo("marsz_v2.pdf");
        assertThat(target.getSizeBytes()).isEqualTo(3L);
        assertThat(target.getSha256()).isEqualTo(SHA_NEW);
        assertThat(target.getPageCount()).isEqualTo(3);
        assertThat(target.getReplacedAt()).isNotNull();
        assertThat(target.getParentFileId())
                .as("a ZIP-extracted child keeps its link back to the parent archive")
                .isEqualTo(999L);
        assertThat(dto.sha256()).isEqualTo(SHA_NEW);
        assertThat(dto.pageCount()).isEqualTo(3);

        // The superseded copy is gone; nothing else's row was rewritten.
        assertThat(oldBytes).doesNotExist();
        assertThat(newerCovering.getReplacedAt()).isNull();
        verify(scoreFileRepository).save(target);
    }

    @Test
    @DisplayName("replace() clears audit pairs of parts bound to the file AND unbound parts resolving to it; other parts untouched")
    void replace_invalidatesAffectedVerificationsOnly() throws IOException {
        ScoreFile target = file(FILE_ID, "application/pdf", "marsz.pdf", 2); // id 20
        realFileOnDisk(target);
        ScoreFile oldScore = file(10L, "application/pdf", "stary_wersja.pdf", 2);
        realFileOnDisk(oldScore);
        ScoreFile tinyNewest = file(30L, "application/pdf", "okladka.pdf", 1);
        realFileOnDisk(tinyNewest);

        CompositionInstrument boundToTarget  = part("Trąbka 1", 1, 2, target);
        CompositionInstrument boundToOther   = part("Trąbka 2", 1, 2, oldScore);
        CompositionInstrument unboundOnTarget = part("Tenor", 2, 2, null);   // newest covering pageTo=2 → id 20
        CompositionInstrument unboundOnOther  = part("Eufonium", 1, 1, null); // newest covering pageTo=1 → id 30
        for (CompositionInstrument p : List.of(boundToTarget, boundToOther, unboundOnTarget, unboundOnOther)) {
            p.verify("librarian", Instant.now());
        }

        when(scoreFileRepository.findById(FILE_ID)).thenReturn(Optional.of(target));
        when(scoreFileRepository.findAllByComposition(composition))
                .thenReturn(List.of(tinyNewest, target, oldScore));
        when(instrumentRepository.findAllByComposition(composition))
                .thenReturn(List.of(boundToTarget, boundToOther, unboundOnTarget, unboundOnOther));
        stubHappyStorageAndPages(4);

        service.replace(newPdfRequest(), FILE_ID, COMP_ID, BAND_ID);

        assertThat(boundToTarget.getVerifiedAt()).isNull();
        assertThat(boundToTarget.getVerifiedBy()).isNull();
        assertThat(unboundOnTarget.getVerifiedAt()).isNull();
        assertThat(boundToOther.getVerifiedAt()).isNotNull();
        assertThat(unboundOnOther.getVerifiedAt()).isNotNull();
        // FK untouched on the bound part — the mapping survives, only the audit falls.
        assertThat(boundToTarget.getScoreFile().getId()).isEqualTo(FILE_ID);
        assertThat(boundToTarget.getPageFrom()).isEqualTo(1);
        assertThat(boundToTarget.getPageTo()).isEqualTo(2);
    }

    @Test
    @DisplayName("replace() on a READY composition demotes it to DRAFT ('Szkic')")
    void replace_readyComposition_demotesToDraft() throws IOException {
        ScoreFile target = file(FILE_ID, "application/pdf", "marsz.pdf", 2);
        realFileOnDisk(target);
        composition.markReady();
        when(scoreFileRepository.findById(FILE_ID)).thenReturn(Optional.of(target));
        when(scoreFileRepository.findAllByComposition(composition)).thenReturn(List.of(target));
        when(instrumentRepository.findAllByComposition(composition)).thenReturn(List.of());
        stubHappyStorageAndPages(2);

        service.replace(newPdfRequest(), FILE_ID, COMP_ID, BAND_ID);

        assertThat(composition.getStatus())
                .isEqualTo(pl.michalbzowski.windband.domain.composition.CompositionStatus.DRAFT);
        verify(compositionRepository).save(composition);
        verify(thumbQueryService).evictFile(FILE_ID);
    }

    @Test
    @DisplayName("replace() keeps an ARCHIVED composition archived (markDraft is a READY-only demotion)")
    void replace_archivedComposition_staysArchived() throws IOException {
        ScoreFile target = file(FILE_ID, "application/pdf", "marsz.pdf", 2);
        realFileOnDisk(target);
        composition.archive();
        when(scoreFileRepository.findById(FILE_ID)).thenReturn(Optional.of(target));
        when(scoreFileRepository.findAllByComposition(composition)).thenReturn(List.of(target));
        when(instrumentRepository.findAllByComposition(composition)).thenReturn(List.of());
        stubHappyStorageAndPages(2);

        service.replace(newPdfRequest(), FILE_ID, COMP_ID, BAND_ID);

        assertThat(composition.getStatus())
                .isEqualTo(pl.michalbzowski.windband.domain.composition.CompositionStatus.ARCHIVED);
    }

    // ---- guards (fail closed, before any disk write) -----------------------------

    @Test
    @DisplayName("replace() rejects a new PDF whose pages do not cover a bound mapping (422) — row, mappings, disk and status untouched")
    void replace_shrinkBelowBoundMapping_rejected() throws IOException {
        ScoreFile target = file(FILE_ID, "application/pdf", "marsz.pdf", 5);
        Path oldBytes = realFileOnDisk(target);
        CompositionInstrument bound = part("Trąbka 1", 3, 5, target);
        bound.verify("librarian", Instant.now());
        composition.markReady();

        when(scoreFileRepository.findById(FILE_ID)).thenReturn(Optional.of(target));
        when(scoreFileRepository.findAllByComposition(composition)).thenReturn(List.of(target));
        when(instrumentRepository.findAllByComposition(composition)).thenReturn(List.of(bound));
        when(pdfPageCounter.extract(any())).thenReturn(3);

        assertThatThrownBy(() -> service.replace(newPdfRequest(), FILE_ID, COMP_ID, BAND_ID))
                .isInstanceOf(UploadRejectedException.class)
                .hasMessageContaining("Trąbka 1")
                .hasMessageContaining("3 stron");

        verify(storage, never()).store(any(), anyLong(), anyString());
        verify(scoreFileRepository, never()).save(any());
        verify(instrumentRepository, never()).save(any());
        verify(compositionRepository, never()).save(any());
        assertThat(target.getPageCount()).isEqualTo(5);
        assertThat(bound.getVerifiedAt()).isNotNull();
        assertThat(composition.getStatus())
                .isEqualTo(pl.michalbzowski.windband.domain.composition.CompositionStatus.READY);
        assertThat(oldBytes).exists();
    }

    @Test
    @DisplayName("replace() refuses to swap a ZIP parent in place (422) — extracted children would serve stale entries")
    void replace_zipParent_rejected() throws IOException {
        ScoreFile zip = file(FILE_ID, "application/zip", "glosy.zip", null);
        realFileOnDisk(zip);
        when(scoreFileRepository.findById(FILE_ID)).thenReturn(Optional.of(zip));

        ScoreFileUploadRequest zipReq = new ScoreFileUploadRequest("glosy_v2.zip", "application/zip", new byte[]{9});
        assertThatThrownBy(() -> service.replace(zipReq, FILE_ID, COMP_ID, BAND_ID))
                .isInstanceOf(UploadRejectedException.class)
                .hasMessageContaining("ZIP");
        verify(storage, never()).store(any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("replace() requires the same MIME type as the replaced row (422)")
    void replace_mimeMismatch_rejected() throws IOException {
        ScoreFile target = file(FILE_ID, "application/pdf", "marsz.pdf", 2);
        realFileOnDisk(target);
        when(scoreFileRepository.findById(FILE_ID)).thenReturn(Optional.of(target));

        ScoreFileUploadRequest zipReq = new ScoreFileUploadRequest("cos.zip", "application/zip", new byte[]{9, 9});
        assertThatThrownBy(() -> service.replace(zipReq, FILE_ID, COMP_ID, BAND_ID))
                .isInstanceOf(UploadRejectedException.class)
                .hasMessageContaining("ten sam typ");
    }

    @Test
    @DisplayName("replace() with a foreign MIME type is rejected by the shared whitelist (415)")
    void replace_disallowedMime_rejected() {
        ScoreFile target = file(FILE_ID, "application/pdf", "marsz.pdf", 2);
        when(scoreFileRepository.findById(FILE_ID)).thenReturn(Optional.of(target));

        ScoreFileUploadRequest req = new ScoreFileUploadRequest("exe", "application/x-msdownload", new byte[]{1});
        assertThatThrownBy(() -> service.replace(req, FILE_ID, COMP_ID, BAND_ID))
                .isInstanceOfSatisfying(UploadRejectedException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(415));
    }

    @Test
    @DisplayName("replace() with empty bytes fails closed (400) before any lookup")
    void replace_emptyBytes_rejected() {
        ScoreFileUploadRequest req = new ScoreFileUploadRequest("p.pdf", "application/pdf", new byte[0]);
        assertThatThrownBy(() -> service.replace(req, FILE_ID, COMP_ID, BAND_ID))
                .isInstanceOfSatisfying(UploadRejectedException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(400));
    }

    @Test
    @DisplayName("replace() of an unknown file id → IllegalStateException (→ 409)")
    void replace_unknownFile_conflict() {
        when(scoreFileRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.replace(newPdfRequest(), 999L, COMP_ID, BAND_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    @DisplayName("replace() with a file whose composition doesn't match the URL → IllegalStateException (→ 409)")
    void replace_wrongComposition_conflict() {
        ScoreFile target = file(FILE_ID, "application/pdf", "marsz.pdf", 2);
        when(scoreFileRepository.findById(FILE_ID)).thenReturn(Optional.of(target));
        assertThatThrownBy(() -> service.replace(newPdfRequest(), FILE_ID, 777L, BAND_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not part of composition");
    }

    @Test
    @DisplayName("replace() with a composition outside the caller's band → IllegalStateException (→ 409)")
    void replace_crossBand_conflict() {
        ScoreFile target = file(FILE_ID, "application/pdf", "marsz.pdf", 2);
        when(scoreFileRepository.findById(FILE_ID)).thenReturn(Optional.of(target));
        when(compositionRepository.findByIdAndBandId(eq(COMP_ID), anyLong())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.replace(newPdfRequest(), FILE_ID, COMP_ID, BAND_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not belong to band");
        verify(thumbQueryService, never()).evictFile(anyLong());
    }

    @Test
    @DisplayName("replace() keeps blank display name (row retains the old originalName)")
    void replace_blankName_keepsOldName() throws IOException {
        ScoreFile target = file(FILE_ID, "application/pdf", "marsz.pdf", 2);
        realFileOnDisk(target);
        when(scoreFileRepository.findById(FILE_ID)).thenReturn(Optional.of(target));
        when(scoreFileRepository.findAllByComposition(composition)).thenReturn(List.of(target));
        when(instrumentRepository.findAllByComposition(composition)).thenReturn(List.of());
        stubHappyStorageAndPages(2);

        service.replace(new ScoreFileUploadRequest(null, "application/pdf", new byte[]{4, 5}),
                FILE_ID, COMP_ID, BAND_ID);

        assertThat(target.getOriginalName()).isEqualTo("marsz.pdf");
        assertThat(target.getReplacedAt()).isNotNull();
    }
}
