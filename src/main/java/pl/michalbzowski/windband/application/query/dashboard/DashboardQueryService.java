package pl.michalbzowski.windband.application.query.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.dashboard.SupersetDashboard;
import pl.michalbzowski.windband.domain.dashboard.SupersetDashboardRepository;

import java.util.List;

/**
 * Query service for dashboard operations.
 * Mediates between controllers and domain repositories.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardQueryService {

    private final SupersetDashboardRepository dashboardRepository;

    public List<SupersetDashboard> findActiveByBandId(Long bandId) {
        return dashboardRepository.findActiveByBandId(bandId);
    }

    public SupersetDashboard findById(Long id) {
        return dashboardRepository.findById(id).orElse(null);
    }

    public List<SupersetDashboard> findAllActive() {
        return dashboardRepository.findByActiveTrueOrderByPositionAsc();
    }
}
