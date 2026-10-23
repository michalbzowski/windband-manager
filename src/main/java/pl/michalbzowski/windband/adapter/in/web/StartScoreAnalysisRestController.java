package pl.michalbzowski.windband.adapter.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.scoreanalysis.ScoreAnalysisCommandService;
import pl.michalbzowski.windband.application.query.llmkey.LlmApiKeyQueryService;
import pl.michalbzowski.windband.domain.llmkey.LlmProvider;

/**
 * US-4.1 — start an AI-analysis of one score file. Band-scoped; the score-file id is
 * validated against the caller's band by the command service (cross-band or missing → 409).
 */
@RestController
@RequestMapping("/bands/{bandId}/compositions/{compositionId}/score-files")
public class StartScoreAnalysisRestController {

    private final ScoreAnalysisCommandService commandService;
    private final LlmApiKeyQueryService llmKeyQueryService;

    public StartScoreAnalysisRestController(ScoreAnalysisCommandService commandService,
                                            LlmApiKeyQueryService llmKeyQueryService) {
        this.commandService = commandService;
        this.llmKeyQueryService = llmKeyQueryService;
    }

    /**
     * POST → 202 Accepted with the new {@code score_analysis} id. The actual pipeline runs
     * in the background (see the plan); the caller polls through
     * {@code GET /analysis/latest} until the row reaches a terminal phase.
     */
    @PostMapping("/{fileId}/analyze")
    public ResponseEntity<String> analyze(
            @PathVariable("bandId") Long bandId,
            @PathVariable("compositionId") Long compositionId,
            @PathVariable("fileId") Long fileId,
            @AuthenticationPrincipal OidcUser principal) {
        // Opcjonalny klucz OpenRouter użytkownika ze skarbca — trafia wyłącznie
        // in-memory do runnera (X-LLM-Key); brak klucza = analiza na providerze serwera.
        String llmApiKey = null;
        if (principal instanceof WindbandOidcUser wu && wu.getUserId() != null) {
            llmApiKey = llmKeyQueryService.plainKey(wu.getUserId(), LlmProvider.OPENROUTER).orElse(null);
        }
        Long analysisId = commandService.start(fileId, bandId, llmApiKey);
        // Plain String body so the standalone MockMvc test (without Jackson2ObjectMapperBuilder
        // configured) still pins the JSON contract — in production (full Spring context),
        // Jackson serialises it to the same JSON string.
        return ResponseEntity.accepted()
                .header("Content-Type", "application/json")
                .body("{\"analysisId\": " + analysisId + "}");
    }
}
