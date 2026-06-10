package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.inventory.AwardItem;

import java.util.List;

/**
 * Spring Data JPA repository for AwardItem.
 * Extends ONLY JpaRepository (NOT the domain interface) to avoid CrudRepository collision.
 */
public interface SpringDataAwardItemRepository extends JpaRepository<AwardItem, Long> {
    List<AwardItem> findByBandIdOrderByDateAwardedDescNameAsc(Long bandId);
    List<AwardItem> findByBandIdAndAssignedMemberIsNotNull(Long bandId);
}
