package pl.michalbzowski.windband.application.command.composition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.application.command.composition.UploadValidator.UploadRejectedException;
import pl.michalbzowski.windband.application.dto.composition.ZipEntryDto;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * US-2.3 — ZIP Content Enumeration integration tests.
 *
 * <p>Drives the real Spring context over Testcontainers PostgreSQL (via
 * {@link BaseIntegrationTest}). Verifies that uploading a ZIP and then calling
 * {@code expandZip} produces one {@code score_files} row per file entry, with
 * correct {@code parent_file_id}, SHA-256, MIME type, and on-disk bytes.</p>
 */
class ScoreFileExpandZipIT extends BaseIntegrationTest {

    @Autowired private BandRepository bandRepository;
    @Autowired private CompositionRepository compositionRepository;
    @Autowired private ScoreFileRepository scoreFileRepository;
    @Autowired private ScoreFileCommandService service;

    private Long bandId;
    private Long compositionId;
    private Composition compositionEntity;

    @BeforeEach
    void setUp() {
        Band band = bandRepository.findById(1L)
                .orElseGet(() -> bandRepository.save(Band.create("Test Band", "test-band-" + System.nanoTime())));
        bandId = band.getId();

        compositionEntity = Composition.create("Expand Fixture", "fixture desc", "composer", null, band);
        compositionEntity = compositionRepository.save(compositionEntity);
        compositionId = compositionEntity.getId();
    }

    @Test
    @DisplayName("expandZip: uploading a 2-file ZIP and expanding produces 3 total rows (1 parent + 2 children)")
    void expandZip_producesChildRows() {
        // Upload the ZIP.
        byte[] zip = makeZipWithPdfAndPng();
        var uploadReq = new ScoreFileUploadRequest("parts.zip", "application/zip", zip);
        var parentDto = service.upload(uploadReq, compositionId, bandId);

        assertThat(parentDto.fileId()).isNotNull();
        assertThat(parentDto.isZip()).isTrue();

        // Before expand: only the parent row exists.
        long countBefore = scoreFileRepository.findAllByComposition(compositionEntity).size();
        assertThat(countBefore).isEqualTo(1);

        // Expand the ZIP.
        List<ZipEntryDto> entries = service.expandZip(parentDto.fileId(), compositionId, bandId);
        assertThat(entries).hasSize(2);

        // After expand: parent + 2 children = 3 rows total.
        List<ScoreFile> allRows = scoreFileRepository.findAllByComposition(compositionEntity);
        assertThat(allRows).hasSize(3);

        // Exactly the two extracted entries carry parent_file_id.
        List<ScoreFile> children = scoreFileRepository.findByParentFileId(parentDto.fileId());
        assertThat(children).hasSize(2);

        // Each child row has a valid SHA-256 and on-disk file.
        for (ScoreFile child : children) {
            assertThat(child.getParentFileId()).isEqualTo(parentDto.fileId());
            assertThat(child.getSha256()).hasSize(64).isNotBlank();
            Path onDisk = Path.of(child.getStoragePath());
            assertThat(onDisk).exists();
            assertThat(onDisk.toFile().length()).isEqualTo((long) child.getSizeBytes());
        }

        // MIME types are correct.
        // Note: order is insertion order (PDF first, PNG second in our fixture).
        ScoreFile pdfChild = children.get(0);
        assertThat(pdfChild.getMimeType()).isEqualTo("application/pdf");

        ScoreFile pngChild = children.get(1);
        assertThat(pngChild.getMimeType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("expandZip: extracted PDFs get their page_count populated")
    void expandZip_pdfEntries_getPageCount() {
        byte[] zip = makeZipWithSinglePdf(TestPdfBuilder.generate(2));
        var uploadReq = new ScoreFileUploadRequest("score.zip", "application/zip", zip);
        var parentDto = service.upload(uploadReq, compositionId, bandId);

        service.expandZip(parentDto.fileId(), compositionId, bandId);

        List<ScoreFile> children = scoreFileRepository.findByParentFileId(parentDto.fileId());
        assertThat(children).hasSize(1);
        assertThat(children.get(0).getMimeType()).isEqualTo("application/pdf");
        assertThat(children.get(0).getPageCount())
                .as("PDF entries extracted from a ZIP should have page_count set (US-2.3)")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("expandZip: extracting the same ZIP twice does not duplicate child rows (idempotent check by count)")
    void expandZip_twice_secondCallStillWorks() {
        byte[] zip = makeZipWithPdfAndPng();
        var uploadReq = new ScoreFileUploadRequest("parts.zip", "application/zip", zip);
        var parentDto = service.upload(uploadReq, compositionId, bandId);

        int countAfterFirst = scoreFileRepository.findAllByComposition(compositionEntity).size();
        assertThat(countAfterFirst).isEqualTo(3); // 1 parent + 2 children

        // Second expand call should add 2 more child rows (no dedup — each call is an explicit user action).
        service.expandZip(parentDto.fileId(), compositionId, bandId);
        int countAfterSecond = scoreFileRepository.findAllByComposition(compositionEntity).size();
        assertThat(countAfterSecond).isEqualTo(5); // 1 parent + 4 children (2 per expand)
    }

    @Test
    @DisplayName("expandZip: non-ZIP file is rejected with 422")
    void expandZip_nonZipFile_rejected() {
        byte[] pdf = TestPdfBuilder.generate(1);
        var uploadReq = new ScoreFileUploadRequest("score.pdf", "application/pdf", pdf);
        var dto = service.upload(uploadReq, compositionId, bandId);

        assertThatThrownBy(() -> service.expandZip(dto.fileId(), compositionId, bandId))
                .isInstanceOf(UploadRejectedException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 422);
    }

    @Test
    @DisplayName("expandZip: ZIP containing a ZIP-slip entry is rejected at extraction time with 422")
    void expandZip_zipWithSlipEntry_rejected() {
        byte[] zip = makeZipWithEntries("../../etc/passwd");
        var uploadReq = new ScoreFileUploadRequest("sneaky.zip", "application/zip", zip);

        // Upload validator should reject this at upload time.
        assertThatThrownBy(() -> service.upload(uploadReq, compositionId, bandId))
                .isInstanceOf(UploadRejectedException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 422);

        assertThat(scoreFileRepository.existsByCompositionId(compositionId)).isFalse();
    }

    // ---- fixtures ------------------------------------------------------------

    /** ZIP with one PDF and one PNG, using real PDF bytes. */
    private byte[] makeZipWithPdfAndPng() {
        try (var baos = new java.io.ByteArrayOutputStream();
             var zos = new java.util.zip.ZipOutputStream(baos)) {
            byte[] pdf = TestPdfBuilder.generate(1);
            zos.putNextEntry(new java.util.zip.ZipEntry("trumpet/part-1.pdf"));
            zos.write(pdf);
            zos.closeEntry();

            // Minimal valid PNG (1x1 black pixel).
            byte[] png = new byte[]{ (byte) 0x89, 0x50, 0x4E, 0x47, (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A };
            zos.putNextEntry(new java.util.zip.ZipEntry("images/cover.png"));
            zos.write(png);
            zos.closeEntry();

            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] makeZipWithSinglePdf(byte[] pdfBytes) {
        try (var baos = new java.io.ByteArrayOutputStream();
             var zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("solo.pdf"));
            zos.write(pdfBytes);
            zos.closeEntry();
            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] makeZipWithEntries(String... names) {
        try (var baos = new java.io.ByteArrayOutputStream();
             var zos = new java.util.zip.ZipOutputStream(baos)) {
            for (String n : names) {
                zos.putNextEntry(new java.util.zip.ZipEntry(n));
                zos.write("x".getBytes());
                zos.closeEntry();
            }
            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
