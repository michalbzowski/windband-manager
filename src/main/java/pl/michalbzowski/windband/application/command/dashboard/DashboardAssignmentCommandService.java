package pl.michalbzowski.windband.application.command.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.dashboard.DashboardBandAssignment;
import pl.michalbzowski.windband.domain.dashboard.DashboardBandAssignmentRepository;
import pl.michalbzowski.windband.domain.dashboard.SupersetDashboard;
import pl.michalbzowski.windband.domain.dashboard.SupersetDashboardRepository;
import pl.michalbzowski.windband.domain.user.AppUser;

import java.util.List;

/**
 * Command service for managing dashboard-band assignments.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardAssignmentCommandService {

    private final SupersetDashboardRepository dashboardRepository;
    private final DashboardBandAssignmentRepository assignmentRepository;
    private final BandRepository bandRepository;

    /**
     * Assigns a dashboard to a band.
     */
    @Transactional
    public void assignDashboardToBand(Long dashboardId, Long bandId, boolean autoAssignNew, AppUser assignedBy) {
        SupersetDashboard dashboard = dashboardRepository.findById(dashboardId)
                .orElseThrow(() -> new IllegalArgumentException("Dashboard not found: " + dashboardId));
        Band band = bandRepository.findById(bandId)
                .orElseThrow(() -> new IllegalArgumentException("Band not found: " + bandId));

        DashboardBandAssignment assignment = assignmentRepository
                .findByDashboardIdAndBandId(dashboardId, bandId)
                .orElseGet(() -> {
                    log.info("Assigning dashboard '{}' to band '{}'", dashboard.getTitle(), band.getName());
                    return DashboardBandAssignment.create(dashboard, band, autoAssignNew, assignedBy);
                });

        assignment.setAutoAssignNew(autoAssignNew);
        assignmentRepository.save(assignment);
    }

    /**
     * Removes a dashboard assignment from a band.
     */
    @Transactional
    public void removeAssignment(Long dashboardId, Long bandId) {
        assignmentRepository.deleteByDashboardIdAndBandId(dashboardId, bandId);
        log.info("Removed dashboard {} assignment from band {}", dashboardId, bandId);
    }

    /**
     * Toggles auto-assign for new bands.
     */
    @Transactional
    public void setAutoAssign(Long dashboardId, Long bandId, boolean autoAssign) {
        DashboardBandAssignment assignment = assignmentRepository
                .findByDashboardIdAndBandId(dashboardId, bandId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        assignment.setAutoAssignNew(autoAssign);
        assignmentRepository.save(assignment);
    }

    /**
     * Assigns all dashboards marked as autoAssignNew to a newly created band.
     */
    @Transactional
    public void autoAssignToNewBand(Band newBand) {
        List<SupersetDashboard> allDashboards = dashboardRepository.findByActiveTrueOrderByPositionAsc();
        for (SupersetDashboard dashboard : allDashboards) {
            boolean alreadyAssigned = assignmentRepository.existsByDashboardIdAndBandId(dashboard.getId(), newBand.getId());
            if (alreadyAssigned) continue;

            // Check if this dashboard has any auto_assign_new assignments
            List<DashboardBandAssignment> assignments = assignmentRepository.findByDashboardId(dashboard.getId());
            boolean hasAutoAssign = assignments.stream().anyMatch(DashboardBandAssignment::isAutoAssignNew);

            if (hasAutoAssign) {
                DashboardBandAssignment assignment = DashboardBandAssignment.create(dashboard, newBand, true, null);
                assignmentRepository.save(assignment);
                log.info("Auto-assigned dashboard '{}' to new band '{}'", dashboard.getTitle(), newBand.getName());
            }
        }
    }
}
