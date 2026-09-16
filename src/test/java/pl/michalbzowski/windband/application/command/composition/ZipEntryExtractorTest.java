package pl.michalbzowski.windband.application.command.composition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.michalbzowski.windband.application.config.ScoresConfig;
import pl.michalbzowski.windband.application.dto.composition.ZipEntryDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * US-2.3 — unit tests for {@link ZipEntryExtractor}: in-memory ZIP unpacking,
 * MIME inference, and ZIP-slip re-validation.
 */
class ZipEntryExtractorTest {

    private ZipEntryExtractor extractor;
    private UploadValidator validator;

    @BeforeEach
    void setUp() {
        ScoresConfig config = new ScoresConfig("/tmp/windband-test", null, null);
        validator = new UploadValidator(config);
        extractor = new ZipEntryExtractor();
    }

    // ---- fixture helpers (same pattern as ScoreFileCommandServiceIT) --------

    static byte[] makeZip(String... entries) {
        try (var baos = new java.io.ByteArrayOutputStream();
             var zos = new java.util.zip.ZipOutputStream(baos)) {
            for (String e : entries) {
                zos.putNextEntry(new java.util.zip.ZipEntry(e));
                byte[] content = ("content-of-" + e).getBytes();
                zos.write(content);
                zos.closeEntry();
            }
            zos.finish();
            return baos.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("extracting a ZIP with two file entries returns both, with inferred MIME types")
    void extract_twoFiles_returnsBoth() {
        byte[] zip = makeZip("trumpet/part-1.pdf", "images/cover.png");

        ZipEntryExtractor.ExtractionResult result = extractor.extract(zip, validator);

        assertThat(result.dtos()).hasSize(2);

        ZipEntryDto pdf = result.dtos().get(0);
        assertThat(pdf.entryName()).isEqualTo("trumpet/part-1.pdf");
        assertThat(pdf.mimeType()).isEqualTo("application/pdf");
        assertThat(pdf.isPdf()).isTrue();
        assertThat(pdf.sizeBytes()).isEqualTo("content-of-trumpet/part-1.pdf".length());

        ZipEntryDto png = result.dtos().get(1);
        assertThat(png.entryName()).isEqualTo("images/cover.png");
        assertThat(png.mimeType()).isEqualTo("image/png");
        assertThat(png.isPdf()).isFalse();

        // Raw bytes list mirrors the DTO list.
        assertThat(result.bytes()).hasSize(2);
    }

    @Test
    @DisplayName("extracting a ZIP with a directory entry skips it — only file entries are returned")
    void extract_directoryEntryIsSkipped() {
        try (var baos = new java.io.ByteArrayOutputStream();
             var zos = new java.util.zip.ZipOutputStream(baos)) {
            // Directory entry
            zos.putNextEntry(new java.util.zip.ZipEntry("trumpet/"));
            zos.closeEntry();
            // File entry
            zos.putNextEntry(new java.util.zip.ZipEntry("trumpet/part-1.pdf"));
            zos.write("x".getBytes());
            zos.closeEntry();
            zos.finish();

            ZipEntryExtractor.ExtractionResult result = extractor.extract(baos.toByteArray(), validator);
            assertThat(result.dtos()).hasSize(1);
            assertThat(result.dtos().get(0).entryName()).isEqualTo("trumpet/part-1.pdf");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("extracting an empty ZIP returns zero entries")
    void extract_emptyZip_returnsEmpty() {
        byte[] zip = makeZip(); // no entries
        ZipEntryExtractor.ExtractionResult result = extractor.extract(zip, validator);
        assertThat(result.dtos()).isEmpty();
        assertThat(result.bytes()).isEmpty();
    }

    @Test
    @DisplayName("ZIP-slip entry is still rejected at extraction time with 422")
    void extract_zipSlipEntry_rejected() {
        byte[] zip = makeZip("../../etc/passwd");
        assertThatThrownBy(() -> extractor.extract(zip, validator))
                .isInstanceOf(UploadValidator.UploadRejectedException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 422);
    }

    @Test
    @DisplayName("extracted byte content matches the original entry content")
    void extract_contentBytesMatch() {
        byte[] expected = "my-part-content".getBytes();
        try (var baos = new java.io.ByteArrayOutputStream();
             var zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("part.pdf"));
            zos.write(expected);
            zos.closeEntry();
            zos.finish();

            ZipEntryExtractor.ExtractionResult result = extractor.extract(baos.toByteArray(), validator);
            assertThat(result.bytes()).hasSize(1);
            assertThat(result.bytes().get(0).content()).isEqualTo(expected);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("MIME inference: unknown extension defaults to application/octet-stream")
    void extract_unknownExtension_isOctetStream() {
        byte[] zip = makeZip("part.xyz");
        ZipEntryExtractor.ExtractionResult result = extractor.extract(zip, validator);
        assertThat(result.dtos().get(0).mimeType()).isEqualTo("application/octet-stream");
        assertThat(result.dtos().get(0).isPdf()).isFalse();
    }

    @Test
    @DisplayName("an entry larger than the per-file cap is rejected with 413 even if the archive passed upload")
    void extract_entryExceedingPerFileCap_rejected() {
        // Default per-file cap is 50 MB; a 60 MB entry must be refused at extraction time.
        byte[] payload = new byte[60 * 1024 * 1024];
        try (var baos = new java.io.ByteArrayOutputStream();
             var zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("huge.pdf"));
            zos.write(payload);
            zos.closeEntry();
            zos.finish();

            assertThatThrownBy(() -> extractor.extract(baos.toByteArray(), validator))
                    .isInstanceOf(UploadValidator.UploadRejectedException.class)
                    .hasFieldOrPropertyWithValue("httpStatus", 413);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("an entry at exactly the per-file cap (50 MB) is accepted — cap is inclusive")
    void extract_entryAtPerFileCap_accepted() {
        byte[] payload = new byte[50 * 1024 * 1024];
        try (var baos = new java.io.ByteArrayOutputStream();
             var zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("edge.pdf"));
            zos.write(payload);
            zos.closeEntry();
            zos.finish();

            ZipEntryExtractor.ExtractionResult result = extractor.extract(baos.toByteArray(), validator);
            assertThat(result.dtos()).hasSize(1);
            assertThat(result.bytes().get(0).sizeBytes()).isEqualTo(50L * 1024 * 1024);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
