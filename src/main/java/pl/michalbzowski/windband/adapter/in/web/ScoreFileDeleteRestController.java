package pl.michalbzowski.windband.adapter.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.michalbzowski.windband.application.command.composition.ScoreFileDeleteCommandService;

/**
 * US-2.5 — {@code DELETE /bands/{bandId}/compositions/{compositionId}/files/{fileId}}.
 *
 * <p>Success returns {@code 204 No Content} (no response body). Rejection mapping is
 * handled by {@link GlobalExceptionHandler}: unknown band → 400, unknown file /
 * cross-band / composition mismatch → 409.</p>
 *
 * <p>Lives in the adapter layer — application-layer logic (band isolation, row +
 * child-row cascade deletion, best-effort on-disk cleanup) is delegated to
 * {@link ScoreFileDeleteCommandService}.</p>
 */
@RestController
@RequestMapping("/bands/{bandId}/compositions/{compositionId}/files")
public class ScoreFileDeleteRestController {

    private final ScoreFileDeleteCommandService deleteCommandService;

    public ScoreFileDeleteRestController(ScoreFileDeleteCommandService deleteCommandService) {
        this.deleteCommandService = deleteCommandService;
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> delete(
            @PathVariable("bandId") Long bandId,
            @PathVariable("compositionId") Long compositionId,
            @PathVariable("fileId") Long fileId) {
        deleteCommandService.delete(fileId, compositionId, bandId);
        return ResponseEntity.noContent().build();
    }
}
