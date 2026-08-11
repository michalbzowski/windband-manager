package pl.michalbzowski.windband.domain.inventory;

import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;
import java.util.Optional;

// Unified repository interface for all inventory items (SINGLE_TABLE inheritance)
public interface InventoryRepository {

    // === New unified methods - will be implemented after refactoring (1.22) ===
    // Temporary placeholders to track progress
    // TODO 1.3: Implement these after Uniform/Instrument/Award inherit from InventoryItem
    /*
    InventoryItem save(InventoryItem item);
    Optional<InventoryItem> findById(Long id);
    List<InventoryItem> findAllByBandId(Long bandId);
    List<InventoryItem> findAllByBandIdAndType(Long bandId, ItemType type);
    List<InventoryItem> findByLocation(Long warehouseId);
    List<InventoryItem> findByExternalOwnerType(Long bandId, ExternalOwnerType externalOwnerType);
    Optional<InventoryItem> findBySystemId(String systemId);
    List<InventoryItem> findByMember(Member member);
    List<InventoryItem> findAvailableItems(Long bandId, ItemType type);
    void delete(InventoryItem item);
    */

    // === Uniform items (legacy - keep for backward compatibility) ===
    UniformItem saveUniformItem(UniformItem item);
    List<UniformItem> findAllUniformItems();
    List<UniformItem> findAllUniformItemsByBandId(Long bandId);
    List<UniformItem> findUniformItemsByMember(Member member);
    Optional<UniformItem> findUniformItemById(Long id);
    void deleteUniformItem(UniformItem item);

    // === Instrument items (legacy - keep for backward compatibility) ===
    InstrumentItem saveInstrumentItem(InstrumentItem item);
    List<InstrumentItem> findAllInstrumentItems();
    List<InstrumentItem> findAllInstrumentItemsByBandId(Long bandId);
    List<InstrumentItem> findInstrumentItemsByMember(Member member);
    Optional<InstrumentItem> findInstrumentItemById(Long id);
    void deleteInstrumentItem(InstrumentItem item);

    // === Orders ===
    InventoryOrder saveOrder(InventoryOrder order);
    List<InventoryOrder> findAllOrders();
    List<InventoryOrder> findAllOrdersByBandId(Long bandId);
    List<InventoryOrder> findOrdersByMember(Member member);
    List<InventoryOrder> findOrdersByStatus(OrderStatus status);
    Optional<InventoryOrder> findOrderById(Long id);

    // === Assignment history ===
    AssetAssignmentHistory saveAssignment(AssetAssignmentHistory assignment);
    void deleteAssignment(AssetAssignmentHistory assignment);
    List<AssetAssignmentHistory> findHistoryByUniformItem(UniformItem item);
    List<AssetAssignmentHistory> findHistoryByInstrumentItem(InstrumentItem item);
    List<AssetAssignmentHistory> findHistoryByMember(Member member);
    List<AssetAssignmentHistory> findActiveAssignments();
}
