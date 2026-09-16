package pl.michalbzowski.windband.application.command.composition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-2.1 ZIP-slip detector — pure logic, no Spring.
 *
 * <p>Each case below pins down which entry names the detector must flag as
 * unsafe (escaping {@code ..}, absolute paths, drive letters) and which it must
 * accept (relative paths inside the extraction directory).</p>
 */
@DisplayName("UploadValidator.zipWithEntry")
class UploadValidatorZipSlipTest {

    private static byte[] zip(String... entries) {
        try (var baos = new java.io.ByteArrayOutputStream();
             var zos = new java.util.zip.ZipOutputStream(baos)) {
            for (String e : entries) {
                zos.putNextEntry(new java.util.zip.ZipEntry(e));
                zos.write("x".getBytes());
                zos.closeEntry();
            }
            zos.finish();
            return baos.toByteArray();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void print(String label, String[] entries) {
        System.out.println(label + "[" + String.join(", ", entries) + "]");
    }

    @Test
    @DisplayName(".. prefix is unsafe")
    void escapeWithDotDot_isFlagged() {
        List<String> unsafe = UploadValidator.unsafeEntries(zip("../../etc/passwd"));
        assertThat(unsafe).containsExactly("../../etc/passwd");
    }

    @Test
    @DisplayName("absolute paths are unsafe")
    void absolutePath_isFlagged() {
        List<String> unsafe = UploadValidator.unsafeEntries(zip("/tmp/x.txt"));
        assertThat(unsafe).containsExactly("/tmp/x.txt");
    }

    @Test
    @DisplayName("drive-letter absolute is unsafe")
    void windowsDriveLetter_isFlagged() {
        List<String> unsafe = UploadValidator.unsafeEntries(zip("C:\\Windows\\x"));
        assertThat(unsafe).isNotEmpty();
    }

    @Test
    @DisplayName("backslash separator (Windows-style) is flagged by its own rule")
    void backslash_isFlagged() {
        List<String> unsafe = UploadValidator.unsafeEntries(zip("a\\b\\c"));
        assertThat(unsafe).isNotEmpty();
    }

    @Test
    @DisplayName("safe relative paths are accepted")
    void safeRelativePath_isClean() {
        List<String> unsafe = UploadValidator.unsafeEntries(
                zip("trumpet-1.pdf", "images/cover.png"));
        assertThat(unsafe).isEmpty();
    }

    @Test
    @DisplayName("empty and null entry list → empty unsafe list")
    void emptyZip_isClean() {
        List<String> unsafe = UploadValidator.unsafeEntries(zip());
        assertThat(unsafe).isEmpty();
    }
}
