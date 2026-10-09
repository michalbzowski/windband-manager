package pl.michalbzowski.windband.domain.inventory;

import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;
import java.util.Optional;

/**
 * Unified repository port for all inventory items (single concrete model).
 */
public interface InventoryItemRepository {

    InventoryItem save(InventoryItem item);

    Optional<InventoryItem> findById(Long id);

    Optional<InventoryItem> findBySystemId(String systemId);

    List<InventoryItem> findAllByBandId(Long bandId);

    List<InventoryItem> findAllByBandIdAndType(Long bandId, ItemType itemType);

    List<InventoryItem> findWithMembersByBandId(Long bandId);

    List<InventoryItem> findAllByAssignedMember(Member member);

    List<InventoryItem> findByAssignedMemberId(Long memberId);

    List<InventoryItem> findAllByLocation(Long warehouseId);

    List<InventoryItem> findByExternalOwnerType(ExternalOwnerType externalOwnerType, Long bandId);

    void delete(InventoryItem item);
}
