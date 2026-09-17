package pl.michalbzowski.windband.adapter.in.web;

import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.michalbzowski.windband.application.query.composition.ScoreFileDownloadQueryService;
import pl.michalbzowski.windband.application.query.composition.ScoreFileDownloadQueryService.Download;
import pl.michalbzowski.windband.application.query.composition.ScoreFileDownloadMetadata;

/**
 * US-2.4 — {@code GET /bands/{bandId}/compositions/{compositionId}/files/{fileId}}.
 *
 * <p>Streams the file's bytes with:
 * <ul>
 *   <li>{@code Content-Disposition: inline; filename="..."} for viewable files (PDF, JPEG, PNG) so a browser
 *       can open them in place;</li>
 *   <li>{@code Content-Disposition: attachment; filename="..."} for ZIPs and other non-viewable binary types;</li>
 *   <li>{@code Content-Type} taken from the row's MIME type, falling back to {@code application/octet-stream};</li>
 *   <li>{@code Content-Length} from the recorded byte size.</li>
 * </ul>
 *
 * <p>Band isolation is enforced inside the query service — see that class for the two-layer contract. Closing the
 * stream is delegated to Spring's container once it has fully read the body; if we do close early (error paths),
 * the best-effort nature of {@link Download#close()} matches the rest of the application layer.</p>
 */
@RestController
@RequestMapping("/bands/{bandId}/compositions/{compositionId}/files")
public class ScoreFileDownloadRestController {

    private final ScoreFileDownloadQueryService downloadService;

    public ScoreFileDownloadRestController(ScoreFileDownloadQueryService downloadService) {
        this.downloadService = downloadService;
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @PathVariable("bandId") Long bandId,
            @PathVariable("compositionId") Long compositionId,
            @PathVariable("fileId") Long fileId) {
        Download handle = downloadService.open(fileId, compositionId, bandId);
        byte[] bytes;
        try (java.io.InputStream in = handle.stream()) {
            bytes = in.readAllBytes();
        } catch (IOException readEx) {
            // The service already validated the file exists and is readable. A mid-stream read failure is a genuine I/O error → 410-equivalent via the same handler path.
            closeQuietly(handle);
            throw new DownloadStreamFailure(readEx, fileId);
        } finally {
            closeQuietly(handle); // idempotent; released on all paths (success, IOException, any RuntimeException).
        }

        org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(bytes);
        return ResponseEntity.status(HttpStatus.OK)
                .headers(headers(handle.metadata()))
                .contentType(contentTypeOf(handle.metadata()))
                .contentLength((long) handle.metadata().sizeBytes())
                .body(resource);
    }

    private static void closeQuietly(Download handle) {
        try {
            handle.close();
        } catch (IOException ignored) {
            // Intentional: the primary result of this request has already been produced (or an exception thrown).
            // A best-effort close must not mask that primary outcome. SpotBugs DE_MIGHT_IGNORE is satisfied by catching it here.
        }
    }

    /** Wraps a mid-stream read failure so {@code GlobalExceptionHandler} maps it to 410 Gone. */
    public static final class DownloadStreamFailure extends IllegalStateException {
        public DownloadStreamFailure(IOException cause, Long fileId) {
            super("Nie udało się odczytać pliku nut (fileId=" + fileId + "): " + cause.getMessage(), cause);
        }
    }

    private static HttpHeaders headers(ScoreFileDownloadMetadata m) {
        String disposition = (m.isZip() ? "attachment" : "inline") + "; filename=\"" + quoted(m) + "\"";
        HttpHeaders h = new HttpHeaders();
        h.put("Content-Disposition", java.util.List.of(disposition));
        return h;
    }

    private static String quoted(ScoreFileDownloadMetadata m) {
        String name = StringUtils.hasText(m.originalName()) ? m.originalName() : ("scorefile-" + m.fileId());
        // RFC 6266 — escape quotes and backslashes in the filename parameter value.
        return name.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static MediaType contentTypeOf(ScoreFileDownloadMetadata m) {
        String mime = m.mimeType();
        if (StringUtils.hasText(mime)) {
            try { return MediaType.parseMediaType(mime); } catch (RuntimeException ignore) { /* fall through */ }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
