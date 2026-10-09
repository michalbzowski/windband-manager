package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.michalbzowski.windband.domain.inventory.InventoryNeed;
import pl.michalbzowski.windband.domain.inventory.ItemType;
import pl.michalbzowski.windband.domain.inventory.NeedStatus;

import java.time.LocalDate;
import java.util.List;

public interface SpringDataInventoryNeedRepository extends JpaRepository<InventoryNeed, Long> {

    List<InventoryNeed> findByBandId(Long bandId);

    List<InventoryNeed> findByBandIdAndStatus(Long bandId, NeedStatus status);

    List<InventoryNeed> findByBandIdAndItemType(Long bandId, ItemType itemType);

    List<InventoryNeed> findByRequestedById(Long memberId);

    List<InventoryNeed> findByOrderedAtBetween(LocalDate from, LocalDate to);

    @Query("SELECT n FROM InventoryNeed n WHERE n.band.id = :bandId AND (n.status = 'NEW' OR n.status = 'FORWARDED_TO_APPROVAL' OR n.status = 'APPROVED' OR n.status = 'ORDERED' OR n.status = 'IN_PROGRESS')")
    List<InventoryNeed> findOpenByBandId(@Param("bandId") Long bandId);

    @Query("SELECT n FROM InventoryNeed n WHERE n.band.id = :bandId AND (n.status = 'ORDERED' OR n.status = 'IN_PROGRESS') AND n.orderedAt IS NOT NULL")
    List<InventoryNeed> findOverdueCandidatesByBandId(@Param("bandId") Long bandId);
}
