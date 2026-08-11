package pl.michalbzowski.windband.domain.inventory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InventoryNeedRepository {

    InventoryNeed save(InventoryNeed need);

    Optional<InventoryNeed> findById(Long id);

    List<InventoryNeed> findAll();

    List<InventoryNeed> findByBandId(Long bandId);

    List<InventoryNeed> findByBandIdAndStatus(Long bandId, NeedStatus status);

    List<InventoryNeed> findByBandIdAndItemType(Long bandId, ItemType itemType);

    List<InventoryNeed> findByRequestedByMemberId(Long memberId);

    List<InventoryNeed> findByDateRange(LocalDate from, LocalDate to);

    List<InventoryNeed> findOpenNeeds(Long bandId);

    List<InventoryNeed> findOverdueOrders(Long bandId);

    void delete(InventoryNeed need);
}