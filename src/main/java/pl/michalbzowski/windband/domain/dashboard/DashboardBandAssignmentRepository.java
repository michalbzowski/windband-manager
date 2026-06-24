package pl.michalbzowski.windband.domain.dashboard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DashboardBandAssignmentRepository extends JpaRepository<DashboardBandAssignment, Long> {

    Optional<DashboardBandAssignment> findByDashboardIdAndBandId(Long dashboardId, Long bandId);

    List<DashboardBandAssignment> findByBandId(Long bandId);

    List<DashboardBandAssignment> findByDashboardId(Long dashboardId);

    boolean existsByDashboardIdAndBandId(Long dashboardId, Long bandId);

    @Query("SELECT a FROM DashboardBandAssignment a WHERE a.dashboard.id = :dashboardId")
    List<DashboardBandAssignment> findAssignmentsForDashboard(@Param("dashboardId") Long dashboardId);

    @Modifying
    @Query("DELETE FROM DashboardBandAssignment a WHERE a.dashboard.id = :dashboardId AND a.band.id = :bandId")
    void deleteByDashboardIdAndBandId(@Param("dashboardId") Long dashboardId, @Param("bandId") Long bandId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM DashboardBandAssignment a " +
           "WHERE a.dashboard.supersetId = :supersetId AND a.band.id = :bandId")
    boolean existsBySupersetIdAndBandId(@Param("supersetId") Integer supersetId, @Param("bandId") Long bandId);
}
