package pl.michalbzowski.windband.application.command.composition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.application.config.ScoresConfig;

/**
 * Performs the actual disk write + SHA-256 computation for a verified upload.
 * Deliberately separate from the validator (SRP): validation is cheap and
 * I/O-light; storage is expensive and may throw I/O errors — separating them
 * keeps each testable in isolation.
 */
@Component
public class ScoreFileStorage {

    private final ScoresConfig config;

    public ScoreFileStorage(ScoresConfig config) {
        this.config = config;
    }

    /**
     * Two-phase write: bytes go to a temp file first (atomic on the same volume),
     * then are moved into the permanent location. The returned record carries the
     * SHA-256 digest computed over the final on-disk bytes so the caller can
     * persist it alongside the {@code score_files} row.
     *
     * <p>Any failure leaves no temp file behind: cleanup is guaranteed in a
     * {@code finally} block, and any secondary failure (e.g. during delete) is
     * suppressed rather than masking the original exception.</p>
     */
    public StoredFile store(InputStream in, long sizeBytes, String originalName) throws IOException {
        Path root = ensureRoot();
        Path tmp = Files.createTempFile(root, "upload-", ".tmp");
        try {
            // 1. Write bytes to the temp file on the same volume as the destination.
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);

            // 2. Re-read for SHA-256 (digests are much safer when computed over the final on-disk bytes).
            String sha;
            try (InputStream shaIn = Files.newInputStream(tmp)) {
                sha = sha256(shaIn);
            }

            // 3. Materialise the destination path and move the file in place.
            Path finalLocation = destination(root, originalName);
            Path parentDir = finalLocation.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            Files.move(tmp, finalLocation, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(finalLocation.toAbsolutePath().toString(), sha, sizeBytes);
        } finally {
            // Best-effort cleanup of any temp file that didn't make it to its destination.
            // Swallow failures here so they never mask the primary exception above.
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException suppressed) {
                // Intentionally silent: a leftover temp file is an operational concern, not a functional one.
            }
        }
    }

    /** Public utility: streaming SHA-256 (used by tests and future report code). */
    public static String sha256(InputStream in) throws IOException {
        java.security.MessageDigest digest;
        try {
            digest = java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing from JRE", e);
        }
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            digest.update(buf, 0, n);
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private Path ensureRoot() throws IOException {
        String configured = (config != null && config.rootPath() != null) ? config.rootPath() : "/tmp/windband-scores";
        Path root = Path.of(configured).toAbsolutePath();
        Files.createDirectories(root);
        return root;
    }

    private Path destination(Path root, String originalName) {
        String safe = sanitize(originalName);
        long now = System.currentTimeMillis();
        return root.resolve(UUID.randomUUID() + "_" + now + "_" + safe);
    }

    /** Strip anything that would let an arbitrary filename escape the target directory. */
    static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        Path p = Path.of(name);
        java.nio.file.Path fileName = p.getFileName();
        String leaf = (fileName != null) ? fileName.toString() : name;
        // Windows reserved + path separators + control chars.
        return leaf.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]", "_");
    }

    /** Result of a successful store: where the file now lives + its checksum. */
    public record StoredFile(String storagePath, String sha256, long sizeBytes) {}
}
