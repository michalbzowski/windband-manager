package pl.michalbzowski.windband.application.query.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.dashboard.DashboardBandAssignmentRepository;
import pl.michalbzowski.windband.domain.dashboard.SupersetDashboard;
import pl.michalbzowski.windband.domain.dashboard.SupersetDashboardRepository;
import pl.michalbzowski.windband.infrastructure.superset.SupersetApiDtos;
import pl.michalbzowski.windband.infrastructure.superset.SupersetClient;

import java.util.List;

/**
 * Service that syncs dashboards from Superset into windband-manager's local DB.
 * Called when an admin opens the dashboard management page.
 *
 * Flow:
 * 1. Fetch all dashboards from Superset API
 * 2. For each dashboard: insert if new, update if exists
 * 3. Auto-assign to new bands where auto_assign_new=true
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DashboardSyncService {

    private final SupersetClient supersetClient;
    private final SupersetDashboardRepository dashboardRepository;
    private final DashboardBandAssignmentRepository assignmentRepository;

    /**
     * Syncs all dashboards from Superset into local database.
     * Returns a summary of changes.
     */
    public DashboardSyncResult syncFromSuperset() {
        DashboardSyncResult result = new DashboardSyncResult();

        if (!supersetClient.isAvailable()) {
            result.addError("Superset is not available");
            return result;
        }

        List<SupersetApiDtos.DashboardEntry> supersetDashboards = supersetClient.listDashboards();
        result.setTotalInSuperset(supersetDashboards.size());

        for (SupersetApiDtos.DashboardEntry entry : supersetDashboards) {
            try {
                syncOneDashboard(entry, result);
            } catch (Exception e) {
                log.error("Failed to sync dashboard {}: {}", entry.getId(), e.getMessage(), e);
                result.addError("Dashboard " + entry.getId() + ": " + e.getMessage());
            }
        }

        log.info("Dashboard sync complete: {} added, {} updated, {} unchanged, {} errors",
                result.getAdded(), result.getUpdated(), result.getUnchanged(), result.getErrors().size());

        return result;
    }

    /**
     * Fetches guest token for a specific dashboard + band combination.
     * The token embeds RLS filtering so user only sees their band's data.
     */
    @Transactional(readOnly = true)
    public String getGuestToken(int dashboardId, Long bandId, String bandName) {
        return supersetClient.generateGuestToken(dashboardId, bandId, bandName);
    }

    private void syncOneDashboard(SupersetApiDtos.DashboardEntry entry, DashboardSyncResult result) {
        Integer supersetId = entry.getId();
        String title = entry.getDashboardTitle();
        String slug = entry.getSlug() != null ? entry.getSlug() : "dashboard-" + supersetId;

        // Unpublished dashboards — skip or deactivate
        if (!"true".equalsIgnoreCase(entry.getPublished())) {
            dashboardRepository.findBySupersetId(supersetId).ifPresent(existing -> {
                if (existing.isActive()) {
                    existing.setActive(false);
                    dashboardRepository.save(existing);
                    result.incrementUpdated();
                }
            });
            return;
        }

        SupersetDashboard existing = dashboardRepository.findBySupersetId(supersetId).orElse(null);

        if (existing == null) {
            // New dashboard — insert
            SupersetDashboard dashboard = new SupersetDashboard(supersetId, title, slug);
            dashboard.setActive(true);
            dashboard.setIcon("fa-chart-bar");
            dashboardRepository.save(dashboard);
            result.incrementAdded();
            log.info("Added new dashboard from Superset: '{}' (id={})", title, supersetId);
        } else {
            // Existing — update if changed
            boolean changed = false;
            if (!title.equals(existing.getTitle()) || !slug.equals(existing.getSlug())) {
                existing.updateFromSuperset(title, slug, null);
                changed = true;
            }
            if (!existing.isActive()) {
                existing.setActive(true);
                changed = true;
            }
            if (changed) {
                dashboardRepository.save(existing);
                result.incrementUpdated();
                log.info("Updated dashboard: '{}' (id={})", title, supersetId);
            } else {
                result.incrementUnchanged();
            }
        }
    }
}
