package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.dashboard.DashboardAssignmentCommandService;
import pl.michalbzowski.windband.application.query.dashboard.DashboardSyncResult;
import pl.michalbzowski.windband.application.query.dashboard.DashboardSyncService;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.dashboard.DashboardBandAssignment;
import pl.michalbzowski.windband.domain.dashboard.DashboardBandAssignmentRepository;
import pl.michalbzowski.windband.domain.dashboard.SupersetDashboard;
import pl.michalbzowski.windband.domain.dashboard.SupersetDashboardRepository;
import pl.michalbzowski.windband.domain.user.AppUser;

import java.util.*;

/**
 * Admin controller for dashboard management.
 * Allows admin to:
 * - Sync dashboards from Superset
 * - Assign dashboards to bands
 * - Configure auto-assign for new bands
 */
@Controller
@RequestMapping("/admin/dashboards")
@RequiredArgsConstructor
public class DashboardAdminController {

    private final DashboardSyncService syncService;
    private final DashboardAssignmentCommandService assignmentService;
    private final SupersetDashboardRepository dashboardRepository;
    private final DashboardBandAssignmentRepository assignmentRepository;
    private final BandRepository bandRepository;
    private final TeamQueryService teamQueryService;

    /**
     * Shows the dashboard management page.
     * Syncs from Superset on each visit to pick up new dashboards.
     */
    @GetMapping
    public String manageDashboards(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        // Sync from Superset — picks up new dashboards
        DashboardSyncResult syncResult = syncService.syncFromSuperset();
        model.addAttribute("syncResult", syncResult);

        // Load all dashboards
        var dashboards = dashboardRepository.findByActiveTrueOrderByPositionAsc();
        var bands = bandRepository.findAll();

        // Build map: dashboardId -> set of assigned bandIds
        Map<Long, Set<Long>> assignedBandIds = new HashMap<>();
        for (var dash : dashboards) {
            Set<Long> assigned = new HashSet<>();
            for (DashboardBandAssignment a : assignmentRepository.findByDashboardId(dash.getId())) {
                assigned.add(a.getBand().getId());
            }
            assignedBandIds.put(dash.getId(), assigned);
        }

        model.addAttribute("dashboards", dashboards);
        model.addAttribute("bands", bands);
        model.addAttribute("assignedBandIds", assignedBandIds);

        return "dashboards/admin";
    }

    /**
     * Syncs dashboards from Superset (manual trigger via HTMX).
     */
    @PostMapping("/sync")
    @ResponseBody
    public String syncDashboards(Model model) {
        DashboardSyncResult result = syncService.syncFromSuperset();
        model.addAttribute("syncResult", result);
        return "dashboards/fragments/admin-sync-result";
    }

    /**
     * Saves dashboard-band assignments (form submit).
     */
    @PostMapping("/save-assignments")
    public String saveAssignments(
            @AuthenticationPrincipal OidcUser oidcUser,
            @RequestParam String dashboardSlug,
            @RequestParam(required = false) List<Long> bandIds,
            @RequestParam(required = false) List<Long> autoAssignBandIds) {

        if (bandIds == null) bandIds = List.of();
        if (autoAssignBandIds == null) autoAssignBandIds = List.of();

        Long userId = ((WindbandOidcUser) oidcUser).getUserId();
        AppUser user = teamQueryService.getAppUser(userId).orElse(null);

        SupersetDashboard dashboard = dashboardRepository.findBySlug(dashboardSlug).orElse(null);
        if (dashboard == null) {
            return "redirect:/admin/dashboards";
        }

        // Remove all existing assignments for this dashboard
        assignmentRepository.findByDashboardId(dashboard.getId()).forEach(a ->
                assignmentService.removeAssignment(dashboard.getId(), a.getBand().getId()));

        // Add new assignments
        for (Long bandId : bandIds) {
            boolean autoAssign = autoAssignBandIds.contains(bandId);
            assignmentService.assignDashboardToBand(dashboard.getId(), bandId, autoAssign, user);
        }

        return "redirect:/admin/dashboards";
    }
}
