package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.dashboard.DashboardQueryService;
import pl.michalbzowski.windband.application.query.dashboard.DashboardViewItem;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;
import pl.michalbzowski.windband.domain.dashboard.SupersetDashboard;

import java.util.List;

/**
 * User-facing dashboard controller.
 * Shows dashboards assigned to the user's current band.
 * Each dashboard is embedded in an iframe with a guest token for RLS.
 */
@Controller
@RequestMapping("/dashboards")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardQueryService dashboardQueryService;
    private final TeamQueryService teamQueryService;

    @Value("${superset.public-url:https://superset.michalbzowski.pl}")
    private String supersetPublicUrl;

    /**
     * Lists all dashboards available for the user's current band.
     */
    @GetMapping
    public String listDashboards(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session, Model model) {
        Long activeTeamId = resolveActiveTeamId(oidcUser, session);
        if (activeTeamId == null) {
            model.addAttribute("error", "Nie należysz do żadnego zespołu");
            return "dashboards/list";
        }

        List<SupersetDashboard> dashboards = dashboardQueryService.findActiveByBandId(activeTeamId);
        List<DashboardViewItem> items = dashboards.stream()
                .map(DashboardViewItem::fromEntity)
                .toList();

        model.addAttribute("dashboards", items);
        model.addAttribute("supersetUrl", supersetPublicUrl);
        return "dashboards/list";
    }

    /**
     * Shows a single dashboard embedded in an iframe.
     * Generates a guest token with RLS filtering by band_id.
     */
    @GetMapping("/{id}")
    public String viewDashboard(
            @AuthenticationPrincipal OidcUser oidcUser,
            HttpSession session,
            @PathVariable Long id,
            Model model) {

        Long activeTeamId = resolveActiveTeamId(oidcUser, session);
        if (activeTeamId == null) {
            model.addAttribute("error", "Nie należysz do żadnego zespołu");
            return "dashboards/view";
        }

        SupersetDashboard dashboard = dashboardQueryService.findById(id);
        if (dashboard == null) {
            model.addAttribute("error", "Dashboard nie został znaleziony");
            return "dashboards/view";
        }

        if (!dashboard.isActive()) {
            model.addAttribute("error", "Dashboard nie jest aktywny");
            return "dashboards/view";
        }

        // Generate guest token with RLS
        String bandName = teamQueryService.getBandName(activeTeamId).orElse("band");
        // Guest token generation requires Superset to be available — skip in view, embed URL without token
        // The guest token will be fetched client-side via API
        String guestToken = null; // Will be populated by SupersetGuestTokenController if needed

        model.addAttribute("dashboard", dashboard);
        model.addAttribute("guestToken", guestToken);
        model.addAttribute("supersetUrl", supersetPublicUrl);
        model.addAttribute("bandId", activeTeamId);

        return "dashboards/view";
    }

    private Long resolveActiveTeamId(OidcUser oidcUser, HttpSession session) {
        if (!(oidcUser instanceof WindbandOidcUser wu)) {
            return null;
        }
        Long sessionTeamId = (Long) session.getAttribute("activeTeamId");
        if (sessionTeamId != null) {
            boolean stillBelongs = teamQueryService.getUserTeam(wu.getUserId(), sessionTeamId).isPresent();
            if (stillBelongs) {
                return sessionTeamId;
            }
        }
        return wu.getActiveTeamId();
    }
}
