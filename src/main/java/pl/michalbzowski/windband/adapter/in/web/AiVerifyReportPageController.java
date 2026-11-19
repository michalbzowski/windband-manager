package pl.michalbzowski.windband.adapter.in.web;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.scoreanalysis.AiVerifyReportDto;
import pl.michalbzowski.windband.application.query.scoreanalysis.AiVerifyReportQueryService;

/**
 * Flagged feature M1 — "raport spójności AI": the mobile-first evidence-queue view that shows the
 * AI's proposals with pre-acceptance consistency verdicts (overlaps, beyond-pages gaps) and lets
 * the user correct a part's page range before accepting it.
 *
 * <p>Feature flag: {@code app.ai-verify-report.enabled} (default OFF). While OFF this route answers
 * 404 — as if the feature did not exist — so a flagged-off deployment is indistinguishable from one
 * without it, and the legacy AI flow in {@code compositions/detail.html} is untouched. When ON, the
 * view reads the latest SUCCEEDED analysis through {@link AiVerifyReportQueryService}; an empty
 * report renders a friendly "brak analizy" state instead of a 500.
 */
@Controller
@RequestMapping("/bands/{bandId}/compositions/{compositionId}")
public class AiVerifyReportPageController {

    private final AiVerifyReportQueryService reportQueryService;
    private final boolean aiVerifyReportEnabled;

    public AiVerifyReportPageController(AiVerifyReportQueryService reportQueryService,
            @Value("${app.ai-verify-report.enabled:false}") boolean aiVerifyReportEnabled) {
        this.reportQueryService = reportQueryService;
        this.aiVerifyReportEnabled = aiVerifyReportEnabled;
    }

    @GetMapping("/verify-ai")
    public String verifyAi(@PathVariable Long bandId,
                           @PathVariable Long compositionId,
                           @AuthenticationPrincipal OidcUser oidcUser,
                           Model model) {
        if (!aiVerifyReportEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Funkcja raportu spójności AI jest wyłączona.", null);
        }
        requireBandAccess(oidcUser, bandId);

        Optional<AiVerifyReportDto> report =
                reportQueryService.reportForLatestSucceeded(compositionId, bandId);
        model.addAttribute("report", report.orElse(null));
        model.addAttribute("bandId", bandId);
        model.addAttribute("compositionId", compositionId);
        return "compositions/verify-ai";
    }

    /** Same guard as {@link CompositionPageController} — cross-team id → deny, never render. */
    private void requireBandAccess(OidcUser oidcUser, Long bandId) {
        if (!(oidcUser instanceof WindbandOidcUser wu) || !wu.belongsToTeam(bandId)) {
            throw new IllegalStateException("Użytkownik nie ma dostępu do zespołu o ID: " + bandId);
        }
    }
}
