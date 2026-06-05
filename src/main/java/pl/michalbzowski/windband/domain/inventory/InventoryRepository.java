package pl.michalbzowski.windband.domain.inventory;

import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository {

    // Uniform items
    UniformItem saveUniformItem(UniformItem item);
    List<UniformItem> findAllUniformItems();
    List<UniformItem> findAllUniformItemsByBandId(Long bandId);
    List<UniformItem> findUniformItemsByMember(Member member);
    Optional<UniformItem> findUniformItemById(Long id);
    void deleteUniformItem(UniformItem item);

    // Instrument items
    InstrumentItem saveInstrumentItem(InstrumentItem item);
    List<InstrumentItem> findAllInstrumentItems();
    List<InstrumentItem> findAllInstrumentItemsByBandId(Long bandId);
    List<InstrumentItem> findInstrumentItemsByMember(Member member);
    Optional<InstrumentItem> findInstrumentItemById(Long id);
    void deleteInstrumentItem(InstrumentItem item);

    // Orders
    InventoryOrder saveOrder(InventoryOrder order);
    List<InventoryOrder> findAllOrders();
    List<InventoryOrder> findAllOrdersByBandId(Long bandId);
    List<InventoryOrder> findOrdersByMember(Member member);
    List<InventoryOrder> findOrdersByStatus(OrderStatus status);
    Optional<InventoryOrder> findOrderById(Long id);

    // Assignment history
    AssetAssignmentHistory saveAssignment(AssetAssignmentHistory assignment);
    List<AssetAssignmentHistory> findHistoryByUniformItem(UniformItem item);
    List<AssetAssignmentHistory> findHistoryByInstrumentItem(InstrumentItem item);
    List<AssetAssignmentHistory> findHistoryByMember(Member member);
    List<AssetAssignmentHistory> findActiveAssignments();
}
