package pl.michalbzowski.windband.adapter.in.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Feature switches as global Thymeleaf model attributes (same shape as {@link ExternalLinksModelAdvice})
 * so view templates can conditionally render flagged screens without a controller-level branch.
 *
 * <p>Currently exposed:
 * <ul>
 *   <li>{@code aiVerifyReportEnabled} — „raport spójności AI” (M1): the new consistency-report
 *       view + corrected-range acceptance. Default OFF; property {@code app.ai-verify-report.enabled}.
 *       When off, {@code /detail.html} hides the entry link entirely and the REST
 *       {@code accept-corrected} endpoint answers 404 — the legacy AI flow is untouched.</li>
 * </ul>
 *
 * <p>A boolean ModelAttribute is deliberately simple (matching {@code inventory-url}): no new
 * feature-flag infrastructure, each switch is one property line + one advice method. The same
 * property is read by the REST controller via {@code @Value}, so view and API stay in lockstep
 * from a single source of truth.
 */
@ControllerAdvice
public class FeatureFlagsModelAdvice {

    private final boolean aiVerifyReportEnabled;

    public FeatureFlagsModelAdvice(
            @Value("${app.ai-verify-report.enabled:false}") boolean aiVerifyReportEnabled) {
        this.aiVerifyReportEnabled = aiVerifyReportEnabled;
    }

    @ModelAttribute("aiVerifyReportEnabled")
    public boolean aiVerifyReportEnabled() {
        return aiVerifyReportEnabled;
    }
}
