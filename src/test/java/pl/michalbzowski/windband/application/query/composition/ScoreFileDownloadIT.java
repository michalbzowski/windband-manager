package pl.michalbzowski.windband.application.query.composition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * US-2.4 — integration test over a real Spring context (H2) for the download query service.
 * Exercises: success path (stream reads real on-disk bytes), cross-band isolation →
 * {@link IllegalStateException}, mismatched composition in the URL → {@link IllegalStateException},
 * missing-on-disk → {@link ScoreFileDownloadQueryService.ScoreFileMissingException}.
 */
class ScoreFileDownloadIT extends BaseIntegrationTest {

    @Autowired
    private BandRepository bandRepository;
    @Autowired
    private CompositionRepository compositionRepository;
    @Autowired
    private ScoreFileRepository scoreFileRepository;
    @Autowired
    private ScoreFileDownloadQueryService service;

    private Long bandA;
    private Long bandB;
    private Composition ownCompositionA; // owned by band 1 (seed "Test Band")
    private Composition ownCompositionB; // owned by band 2 (seed "Other Band") — for the cross-band isolation case
    private Path tempDir;

    /** data.sql seeds exactly two bands: id=1 name='Test Band' and id=2 name='Other Band' (see src/test/resources/data.sql). */
    private static final long SEED_BAND_A = 1L;
    private static final long SEED_BAND_B = 2L;

    @BeforeEach
    void setUp() throws Exception {
        bandA = SEED_BAND_A;
        bandB = SEED_BAND_B;

        ownCompositionA = compositionRepository.save(
                Composition.create("Utwor A (it)", "desc", "Czajkowski", null, bandOf(bandA)));
        ownCompositionB = compositionRepository.save(
                Composition.create("Utwor B (it)", "desc", "Czajkowski", null, bandOf(bandB)));

        tempDir = Files.createTempDirectory("scorefile-dl-it-");
    }

    private Band bandOf(Long id) { return bandRepository.findById(id).orElseThrow(); }

    @Test
    @DisplayName("download of a PDF owned by the requested band streams its recorded byte size and readable content")
    void downloadExistingFile_success() throws Exception {
        String target = tempDir.resolve("sample.pdf").toAbsolutePath().toString();
        Files.writeString(Path.of(target), "PDF-BYTES");

        ScoreFile row = scoreFileRepository.save(ScoreFile.forComposition(
                ownCompositionA, "application/pdf", "score.pdf", (long) Files.size(Path.of(target)),
                "sha-" + System.nanoTime(), target, 1));

        try (ScoreFileDownloadQueryService.Download handle = service.open(row.getId(), ownCompositionA.getId(), bandA)) {
            assertThat(handle.metadata().fileId()).isEqualTo(row.getId());
            assertThat(handle.metadata().originalName()).isEqualTo("score.pdf");
            byte[] buf = new byte[64];
            int n = handle.stream().read(buf);
            assertThat(n).isEqualTo("PDF-BYTES".length());
        }
    }

    @Test
    @DisplayName("download of a ZIP file flags isZip=true (adapter maps this to Content-Disposition attachment)")
    void downloadZip_isZipTrue() throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (var zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("trumpet-page-1.pdf"));
            zos.write("hi".getBytes());
            zos.closeEntry();
        }
        byte[] zipBytes = baos.toByteArray();
        Path p = tempDir.resolve("parts.zip");
        Files.write(p, zipBytes);

        ScoreFile row = scoreFileRepository.save(ScoreFile.forComposition(
                ownCompositionA, "application/zip", "parts.zip", (long) zipBytes.length,
                "sha-" + System.nanoTime(), p.toString(), null));

        try (ScoreFileDownloadQueryService.Download handle = service.open(row.getId(), ownCompositionA.getId(), bandA)) {
            assertThat(handle.metadata().isZip()).isTrue();
        }
    }

    @Test
    @DisplayName("attempted download of a file whose composition is owned by another band — IllegalStateException (→ 409)")
    void downloadCrossBand_isolated() throws Exception {
        String target = tempDir.resolve("cross.pdf").toAbsolutePath().toString();
        Files.writeString(Path.of(target), "other-band-bytes");

        ScoreFile row = scoreFileRepository.save(ScoreFile.forComposition(
                ownCompositionA, "application/pdf", "score.pdf", (long) Files.size(Path.of(target)),
                "sha-" + System.nanoTime(), target, null));

        // Caller from band B (id 2) asks for a file owned by composition A (in band A's scope) → 409.
        assertThatThrownBy(() -> service.open(row.getId(), ownCompositionA.getId(), bandB))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not belong to band " + bandB);
    }

    @Test
    @DisplayName("download with a composition-id in the URL that doesn't own the file — IllegalStateException (→ 409)")
    void downloadMismatchedComposition_rejected() throws Exception {
        String target = tempDir.resolve("mismatch.pdf").toAbsolutePath().toString();
        Files.writeString(Path.of(target), "x");

        ScoreFile row = scoreFileRepository.save(ScoreFile.forComposition(
                ownCompositionA, "application/pdf", "score.pdf", 1L,
                "sha-" + System.nanoTime(), target, null));

        // URL carries a different composition id from the one owning this file — rejected.
        assertThatThrownBy(() -> service.open(row.getId(), ownCompositionB.getId(), bandA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not part of composition");
    }

    @Test
    @DisplayName("download when the recorded storage path is missing — ScoreFileMissingException (→ HTTP 410)")
    void downloadPathMissingOnDisk_gone() {
        String never = tempDir.resolve("never.pdf").toAbsolutePath().toString();
        assertThat(Path.of(never)).doesNotExist();

        ScoreFile row = scoreFileRepository.save(ScoreFile.forComposition(
                ownCompositionA, "application/pdf", "missing.pdf", 64L,
                "sha-" + System.nanoTime(), never, null));

        assertThatThrownBy(() -> service.open(row.getId(), ownCompositionA.getId(), bandA))
                .isInstanceOf(ScoreFileDownloadQueryService.ScoreFileMissingException.class);
    }
}
