package pl.michalbzowski.windband.domain.dashboard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupersetDashboardRepository extends JpaRepository<SupersetDashboard, Long> {

    Optional<SupersetDashboard> findBySupersetId(Integer supersetId);

    Optional<SupersetDashboard> findBySlug(String slug);

    List<SupersetDashboard> findByActiveTrueOrderByPositionAsc();

    boolean existsBySupersetId(Integer supersetId);

    @Query("SELECT d FROM SupersetDashboard d WHERE d.active = true AND d.id IN " +
           "(SELECT a.dashboard.id FROM DashboardBandAssignment a WHERE a.band.id = :bandId) " +
           "ORDER BY d.position ASC")
    List<SupersetDashboard> findActiveByBandId(@Param("bandId") Long bandId);
}
