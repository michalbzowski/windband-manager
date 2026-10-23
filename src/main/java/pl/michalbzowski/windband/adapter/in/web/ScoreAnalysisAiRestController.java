package pl.michalbzowski.windband.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.scoreanalysis.AcceptAiProposalsCommandService;
import pl.michalbzowski.windband.application.query.scoreanalysis.ProposalsPreviewDto;
import pl.michalbzowski.windband.application.query.scoreanalysis.ScoreAnalysisProposalsQueryService;

/**
 * Faza D — the two human-in-the-loop endpoints of one finished analysis:
 * <ul>
 *   <li>{@code GET  .../proposals} — resolved AI proposals (+ work metadata + band instruments);</li>
 *   <li>{@code POST .../accept}    — create the accepted part rows (verified by the caller, US-4.5).
 * </ul>
 * JSON is written through {@link ObjectMapper} (Phase B convention) so standalone MockMvc tests pin
 * the contract without a configured Jackson module.
 */
@RestController
@RequestMapping("/bands/{bandId}/compositions/{compositionId}/analysis/{analysisId}")
public class ScoreAnalysisAiRestController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ScoreAnalysisProposalsQueryService queryService;
    private final AcceptAiProposalsCommandService commandService;

    public ScoreAnalysisAiRestController(ScoreAnalysisProposalsQueryService queryService,
                                         AcceptAiProposalsCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    /** GET → 200 {@code {work, instruments[], proposals[]}} (409 when the run is not finished). */
    @GetMapping("/proposals")
    public ResponseEntity<String> proposals(@PathVariable("bandId") Long bandId,
                                            @PathVariable("compositionId") Long compositionId,
                                            @PathVariable("analysisId") Long analysisId) throws IOException {
        ProposalsPreviewDto dto = queryService.preview(compositionId, bandId, analysisId);
        return json(MAPPER.writeValueAsString(dto));
    }

    /** The rows the user accepted out of the preview (subset, ranges verbatim). */
    public record AcceptBody(List<AcceptAiProposalsCommandService.AcceptedRow> rows) {}

    /** POST → 200 {@code {created, skipped[]}}; IAE → 400, ISE → 409 by the GlobalExceptionHandler. */
    @PostMapping("/accept")
    public ResponseEntity<String> accept(@PathVariable("bandId") Long bandId,
                                         @PathVariable("compositionId") Long compositionId,
                                         @PathVariable("analysisId") Long analysisId,
                                         @RequestBody AcceptBody body,
                                         @AuthenticationPrincipal OidcUser principal) throws IOException {
        String verifier = verifierOf(principal);
        List<AcceptAiProposalsCommandService.AcceptedRow> rows =
                body == null ? null : body.rows();
        AcceptAiProposalsCommandService.AcceptResult result = commandService.accept(
                compositionId, bandId, analysisId, rows, verifier);
        return json(MAPPER.writeValueAsString(result));
    }

    /** Acceptance stamps the US-4.5 audit pair — fall back to a stable system tag if no user is bound. */
    private static String verifierOf(Object principal) {
        if (principal instanceof WindbandOidcUser wu && wu.getUserId() != null) {
            return String.valueOf(wu.getUserId());
        }
        return "ai-accept";
    }

    private static ResponseEntity<String> json(String body) {
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(body);
    }
}
