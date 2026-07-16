package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.michalbzowski.windband.domain.inventory.AwardItem;

import java.util.List;

/**
 * Spring Data JPA repository for AwardItem.
 * Extends ONLY JpaRepository (NOT the domain interface) to avoid CrudRepository collision.
 */
public interface SpringDataAwardItemRepository extends JpaRepository<AwardItem, Long> {
    @Query("SELECT a FROM AwardItem a LEFT JOIN FETCH a.assignedMember WHERE a.band.id = :bandId ORDER BY a.dateAwarded DESC, a.name ASC")
    List<AwardItem> findByBandIdOrderByDateAwardedDescNameAsc(@Param("bandId") Long bandId);
    @Query("SELECT a FROM AwardItem a LEFT JOIN FETCH a.assignedMember WHERE a.band.id = :bandId AND a.assignedMember IS NOT NULL")
    List<AwardItem> findByBandIdAndAssignedMemberIsNotNull(@Param("bandId") Long bandId);
}
