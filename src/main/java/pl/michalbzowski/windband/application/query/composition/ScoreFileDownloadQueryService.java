package pl.michalbzowski.windband.application.query.composition;

import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;

/**
 * Read side of US-2.4: resolve which file the caller is allowed to download, and open a
 * streaming handle for its bytes without any Spring Web dependency (ArchUnit rule: the
 * application layer must not import {@code org.springframework.web..}).
 *
 * <p>Band isolation follows the project two-layer contract: layer 1 — band exists via
 * {@link BandQueryService#getRequiredBand(Long)}; layer 2 — file owned by that band AND its
 * composition matches the URL.</p>
 */
@Service
@RequiredArgsConstructor
public class ScoreFileDownloadQueryService {

    private final BandQueryService bandQueryService;
    private final ScoreFileRepository scoreFileRepository;

    /**
     * Locate the file within the band/composition, and return an open stream + scalar metadata.
     *
     * <ul>
       *   <li>{@link IllegalArgumentException} if the band does not exist (→ HTTP 400) — layer 1.</li>
       *   <li>{@link IllegalStateException} if the file belongs to another band/composition (→ HTTP 409).</li>
       *   <li>{@link ScoreFileMissingException} if the on-disk path is missing or unreadable (→ HTTP 410).</li>
     * </ul>
     * The caller MUST close the returned handle (try-with-resources). Service never keeps it.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, readOnly = true)
    public Download open(Long fileId, Long compositionId, Long bandId) {
        // Layer 1 — band must exist (IllegalArgumentException → 400 via GlobalExceptionHandler).
        bandQueryService.getRequiredBand(bandId);

        // Layer 2.1 — row exists. If empty the file is unknown in this system; the caller is asking
        // about a non-existent id which is not an ownership conflict, it is a missing resource.
        ScoreFile file = scoreFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalStateException("Score file " + fileId + " was not found."));

        // Layer 2.2 — the URL composition must own this file (prevents path confusion).
        if (!file.getComposition().getId().equals(compositionId)) {
            throw new IllegalStateException("Score file " + fileId + " is not part of composition " + compositionId);
        }

        // Layer 2.3 — the composition must belong to the requested band (the actual band-isolation gate).
        Long owningBand = file.getComposition().getBand().getId();
        if (!owningBand.equals(bandId)) {
            throw new IllegalStateException("Score file " + fileId + " does not belong to band " + bandId);
        }

        final String storagePath = file.getStoragePath();
        boolean zip = "application/zip".equalsIgnoreCase(file.getMimeType());

        java.nio.file.Path path = java.nio.file.Path.of(storagePath);
        if (!java.nio.file.Files.exists(path) || !java.nio.file.Files.isReadable(path)) {
            throw new ScoreFileMissingException(
                    "Nie udało się odczytać pliku " + fileId + " z dysku.");
        }

        InputStream stream;
        try {
            stream = java.nio.file.Files.newInputStream(path);
        } catch (IOException e) {
            throw new ScoreFileMissingException(
                    "Nie udało się otworzyć pliku " + fileId + " do odczytu.", e);
        }

        return new Download(stream,
                new ScoreFileDownloadMetadata(
                        file.getId(),
                        file.getOriginalName(),
                        file.getMimeType(),
                        file.getSizeBytes(),
                        zip));
    }

    /** A closeable handle for the caller to stream. Use try-with-resources; the service does not keep one. */
    public static final class Download implements java.io.Closeable {
        private final InputStream stream;
        private final ScoreFileDownloadMetadata metadata;
        private boolean closed = false;
        public Download(InputStream stream, ScoreFileDownloadMetadata metadata) {
            this.stream = stream;
            this.metadata = metadata;
        }
        public InputStream stream() { return stream; }
        public ScoreFileDownloadMetadata metadata() { return metadata; }
        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                stream.close();
            }
        }
    }

    /** Raised when the recorded storage path is missing on disk or unreadable. Mapped to HTTP 410 Gone in the adapter layer. */
    public static class ScoreFileMissingException extends RuntimeException {
        public ScoreFileMissingException(String m) { super(m); }
        public ScoreFileMissingException(String m, Throwable c) { super(m, c); }
    }
}
