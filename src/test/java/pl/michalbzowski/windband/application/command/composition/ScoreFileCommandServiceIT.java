package pl.michalbzowski.windband.application.command.composition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.application.command.composition.UploadValidator.UploadRejectedException;
import pl.michalbzowski.windband.application.dto.composition.ScoreFileDto;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * US-2.1 — secure file upload pipeline (validator + storage + row persistence).
 *
 * <p>Drives the real Spring context over Testcontainers PostgreSQL (via
 * {@link BaseIntegrationTest}). The assertions check: row shape in PostgreSQL,
 * on-disk byte layout, and SHA-256 equality against the original bytes. No HTTP
 * layer involved — that slice lives in the Web test.</p>
 *
 * <p>All persistence lookups go through composition-scoped accessors on the
 * {@link ScoreFileRepository} domain port (it carries no {@code count()} or
 * {@code findAll()} because the interface is intentionally read-scoped to one
 * composition).</p>
 */
class ScoreFileCommandServiceIT extends BaseIntegrationTest {

    @Autowired private BandRepository bandRepository;
    @Autowired private CompositionRepository compositionRepository;
    @Autowired private ScoreFileRepository scoreFileRepository;
    @Autowired private ScoreFileCommandService service;

    private Long bandId;
    private Long compositionId;
    private Composition compositionEntity; // fresh per test, so scoping queries stay exact

    @BeforeEach
    void setUp() {
        Band band = bandRepository.findById(1L)
                .orElseGet(() -> bandRepository.save(Band.create("Test Band", "test-band-" + System.nanoTime())));
        bandId = band.getId();

        compositionEntity = Composition.create("Upload Fixture", "fixture desc", "composer", null, band);
        compositionEntity = compositionRepository.save(compositionEntity);
        compositionId = compositionEntity.getId();
    }

    @Test
    @DisplayName("upload accepts a valid PDF, stores bytes on disk, and persists one score_files row with SHA-256")
    void uploadValidPdf_success() {
        byte[] pdf = minimalPdfBytes();
        ScoreFileUploadRequest request = new ScoreFileUploadRequest("score.pdf", "application/pdf", pdf);

        ScoreFileDto dto = service.upload(request, compositionId, bandId);

        assertThat(dto.fileId()).isNotNull();
        assertThat(dto.compositionId()).isEqualTo(compositionId);
        assertThat(dto.originalName()).isEqualTo("score.pdf");
        assertThat(dto.sha256()).hasSize(64).isNotBlank();
        assertThat(dto.isZip()).isFalse();
        assertThat(dto.sizeBytes()).isEqualTo((long) pdf.length);

        // One row in the DB, with the expected SHA-256 and bytes recorded.
        List<ScoreFile> rows = scoreFileRepository.findAllByComposition(compositionEntity);
        assertThat(rows).hasSize(1);
        ScoreFile saved = rows.get(0);
        assertThat(saved.getMimeType()).isEqualTo("application/pdf");
        assertThat((long) saved.getSizeBytes()).isEqualTo((long) pdf.length);
        assertThat(saved.getOriginalName()).isEqualTo("score.pdf");
        assertThat(saved.getSha256())
                .as("recorded SHA-256 must equal the digest of the original bytes")
                .isEqualTo(sha256Hex(pdf));

        // Bytes on disk match the recorded path and size.
        java.nio.file.Path onDisk = java.nio.file.Path.of(saved.getStoragePath());
        assertThat(onDisk).exists();
        assertThat(onDisk.toFile().length()).isEqualTo((long) pdf.length);
    }

    @Test
    @DisplayName("upload rejects text/plain with 415 and no row is persisted")
    void uploadDisallowedMime_rejected() {
        ScoreFileUploadRequest request =
                new ScoreFileUploadRequest("notes.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> service.upload(request, compositionId, bandId))
                .isInstanceOf(UploadRejectedException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 415);

        assertThat(scoreFileRepository.existsByCompositionId(compositionId)).isFalse();
        assertThat(scoreFileRepository.findAllByComposition(compositionEntity)).isEmpty();
    }

    @Test
    @DisplayName("upload rejects a ZIP containing an escaping '../' entry with 422 and no row is persisted")
    void uploadZipWithSlipEntry_rejected() {
        ScoreFileUploadRequest request = new ScoreFileUploadRequest(
                "sneak.zip", "application/zip", zipWithEntries("../../etc/passwd"));

        assertThatThrownBy(() -> service.upload(request, compositionId, bandId))
                .isInstanceOf(UploadRejectedException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 422);

        assertThat(scoreFileRepository.existsByCompositionId(compositionId)).isFalse();
    }

    @Test
    @DisplayName("upload accepts a ZIP with safe relative entries")
    void uploadZipSafeEntry_success() {
        ScoreFileUploadRequest request = new ScoreFileUploadRequest(
                "parts.zip", "application/zip", zipWithEntries("trumpet-page-1.pdf", "images/cover.png"));

        ScoreFileDto dto = service.upload(request, compositionId, bandId);

        assertThat(dto.isZip()).isTrue();
        assertThat(dto.fileId()).isNotNull();
        assertThat(dto.originalName()).isEqualTo("parts.zip");

        List<ScoreFile> rows = scoreFileRepository.findAllByComposition(compositionEntity);
        assertThat(rows).hasSize(1);
        ScoreFile saved = rows.get(0);
        assertThat(saved.getMimeType()).isEqualTo("application/zip");
    }

    // ---- helpers ---------------------------------------------------------

    /** Minimal but structurally-valid PDF. The pipeline only cares about bytes + MIME. */
    static byte[] minimalPdfBytes() {
        return """
                %PDF-1.4
                1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
                2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
                3 0 obj << /Type /Page /Parent 2 0 R >> endobj
                trailer << /Size 4 /Root 1 0 R >>
                startxref
                0
                %%EOF
                """.getBytes();
    }

    static byte[] zipWithEntries(String... names) {
        try (var baos = new java.io.ByteArrayOutputStream();
             var zos = new java.util.zip.ZipOutputStream(baos)) {
            for (String n : names) {
                zos.putNextEntry(new java.util.zip.ZipEntry(n));
                zos.write("x".getBytes());
                zos.closeEntry();
            }
            zos.finish();
            return baos.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
