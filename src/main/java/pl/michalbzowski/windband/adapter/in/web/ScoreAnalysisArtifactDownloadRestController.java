package pl.michalbzowski.windband.adapter.in.web;

import java.io.IOException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.michalbzowski.windband.application.query.scoreanalysis.ScoreAnalysisArtifactQueryService;

/**
 * US-4.2 — {@code GET /bands/{bandId}/compositions/{compositionId}/files?artifact={json|musicxml|mid|txt}}.
 *
 * <p>Streams one artefact of the composition's LATEST AI analysis as an attachment, using the exact
 * link contract the composition dialog already renders (see {@code detail.html} → #artifact-links).
 * Band isolation and availability validation live in {@link ScoreAnalysisArtifactQueryService}; unknown
 * artefacts (400) and cross-band / missing-file / non-successful phase (409) surface through the
 * GlobalExceptionHandler. A mid-stream read failure throws
 * {@link DownloadStreamFailure}, which extends IllegalStateException so the existing handler path applies.
 */
@RestController
@RequestMapping("/bands/{bandId}/compositions/{compositionId}/files")
public class ScoreAnalysisArtifactDownloadRestController {

    private final ScoreAnalysisArtifactQueryService artifactService;

    public ScoreAnalysisArtifactDownloadRestController(ScoreAnalysisArtifactQueryService artifactService) {
        this.artifactService = artifactService;
    }

    @GetMapping
    public ResponseEntity<Resource> download(
            @PathVariable("bandId") Long bandId,
            @PathVariable("compositionId") Long compositionId,
            @RequestParam(value = "artifact", required = false) String artifact) {
        ScoreAnalysisArtifactQueryService.Artifact resolved = artifactService.open(compositionId, bandId, artifact);

        byte[] bytes;
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(resolved.path())) {
            bytes = in.readAllBytes();
        } catch (IOException readEx) {
            // The service already validated the file exists and is readable. Surface a mid-stream read
            // failure through the state-exception handler path (kept next to the request scope).
            throw new DownloadStreamFailure(readEx, compositionId);
        }

        ByteArrayResource resource = new ByteArrayResource(bytes);
        return ResponseEntity.status(HttpStatus.OK)
                .headers(headers(resolved.fileName()))
                .contentType(resolved.mediaType())
                .contentLength((long) bytes.length)
                .body(resource);
    }

    /** I/O failure while streaming an artefact — maps through the IllegalStateException handler path. */
    public static final class DownloadStreamFailure extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        public DownloadStreamFailure(IOException cause, Long compositionId) {
            super("Nie udało się odczytać artefaktu analizy utworu " + compositionId + ": " + cause.getMessage(), cause);
        }
    }

    private static HttpHeaders headers(String fileName) {
        String disposition = "attachment; filename=\"" + quoted(fileName) + "\"";
        HttpHeaders h = new HttpHeaders();
        h.put("Content-Disposition", java.util.List.of(disposition));
        return h;
    }

    /** RFC 6266 — escape quotes and backslashes in the filename parameter value. */
    private static String quoted(String name) {
        return name.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
