package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.michalbzowski.windband.domain.inventory.ExternalOwnerType;
import pl.michalbzowski.windband.domain.inventory.InventoryItem;
import pl.michalbzowski.windband.domain.inventory.ItemType;

import java.util.List;
import java.util.Optional;

public interface SpringDataInventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    List<InventoryItem> findByBandId(Long bandId);

    List<InventoryItem> findByBandIdAndType(Long bandId, ItemType type);

    List<InventoryItem> findByAssignedMemberId(Long memberId);

    Optional<InventoryItem> findBySystemId(String systemId);

    @Query("SELECT i FROM InventoryItem i WHERE i.location.id = :warehouseId")
    List<InventoryItem> findByLocation(@Param("warehouseId") Long warehouseId);

    List<InventoryItem> findByExternalOwnerTypeAndBandId(ExternalOwnerType externalOwnerType, Long bandId);

    @Query("SELECT i FROM InventoryItem i LEFT JOIN FETCH i.assignedMember WHERE i.band.id = :bandId ORDER BY i.type, i.name")
    List<InventoryItem> findWithMembersByBandId(@Param("bandId") Long bandId);
}
