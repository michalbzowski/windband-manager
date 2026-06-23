package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.dashboard.DashboardQueryService;
import pl.michalbzowski.windband.application.query.dashboard.DashboardSyncService;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;
import pl.michalbzowski.windband.domain.dashboard.SupersetDashboard;

import java.util.Map;

/**
 * API endpoint for generating Superset guest tokens.
 * Called client-side when loading a dashboard iframe.
 */
@RestController
@RequestMapping("/api/dashboards")
@RequiredArgsConstructor
public class SupersetGuestTokenController {

    private final DashboardQueryService dashboardQueryService;
    private final DashboardSyncService syncService;
    private final TeamQueryService teamQueryService;

    /**
     * Generates a guest token for a specific dashboard + band combination.
     * The token includes RLS filtering so the user only sees their band's data.
     *
     * GET /api/dashboards/{id}/guest-token
     */
    @GetMapping("/{id}/guest-token")
    public ResponseEntity<?> getGuestToken(
            @AuthenticationPrincipal OidcUser oidcUser,
            @PathVariable Long id) {

        if (!(oidcUser instanceof WindbandOidcUser wu)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        SupersetDashboard dashboard = dashboardQueryService.findById(id);
        if (dashboard == null || !dashboard.isActive()) {
            return ResponseEntity.status(404).body(Map.of("error", "Dashboard not found"));
        }

        Long bandId = wu.getActiveTeamId();
        if (bandId == null) {
            return ResponseEntity.status(400).body(Map.of("error", "No active band"));
        }

        String bandName = teamQueryService.getBandName(bandId).orElse("band");
        String token = syncService.getGuestToken(dashboard.getSupersetUuid(), bandId, bandName);

        if (token == null) {
            return ResponseEntity.status(503).body(Map.of("error", "Superset unavailable"));
        }

        return ResponseEntity.ok(Map.of("token", token));
    }
}
