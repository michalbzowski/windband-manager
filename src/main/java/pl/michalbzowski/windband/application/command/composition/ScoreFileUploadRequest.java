package pl.michalbzowski.windband.application.command.composition;

import java.util.Objects;

/**
 * Immutable in-memory representation of an uploaded score file, owned by the
 * application layer. Decoupled from {@code MultipartFile} so the service does
 * not depend on Spring Web types (ArchitectureTest rule: the {@code ..application..}
 * package must never reference {@code org.springframework.web..}).
 *
 * <p>{@link #originalFileName()} carries whatever the browser sent, including its
 * extension — that is exactly what the user chose to upload. {@link #contentType()}
 * is the MIME type the client declared; it drives the validator's whitelist + cap.</p>
 */
public record ScoreFileUploadRequest(
        String originalFileName,
        String contentType,
        byte[] bytes) {

    public ScoreFileUploadRequest {
        Objects.requireNonNull(bytes, "bytes must not be null");
    }

    public long size() { return bytes.length; }

    @Override
    public String toString() {
        return "ScoreFileUploadRequest{name='%.20s', contentType='%s', bytes=%d}"
                .formatted(originalFileName, contentType, size());
    }
}
