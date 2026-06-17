package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.inventory.*;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class InventoryRepositoryAdapter implements InventoryRepository {

    private final SpringDataUniformItemRepository uniformRepo;
    private final SpringDataInstrumentItemRepository instrumentRepo;
    private final SpringDataInventoryOrderRepository orderRepo;
    private final SpringDataAssetAssignmentHistoryRepository historyRepo;

    @Override
    public UniformItem saveUniformItem(UniformItem item) {
        return uniformRepo.save(item);
    }

    @Override
    public List<UniformItem> findAllUniformItems() {
        return uniformRepo.findAllWithMember();
    }

    @Override
    public List<UniformItem> findAllUniformItemsByBandId(Long bandId) {
        return uniformRepo.findByBandId(bandId);
    }

    @Override
    public List<UniformItem> findUniformItemsByMember(Member member) {
        return uniformRepo.findByAssignedMember(member);
    }

    @Override
    public Optional<UniformItem> findUniformItemById(Long id) {
        return uniformRepo.findById(id);
    }

    @Override
    public void deleteUniformItem(UniformItem item) {
        uniformRepo.delete(item);
    }

    @Override
    public InstrumentItem saveInstrumentItem(InstrumentItem item) {
        return instrumentRepo.save(item);
    }

    @Override
    public List<InstrumentItem> findAllInstrumentItems() {
        return instrumentRepo.findAllWithMember();
    }

    @Override
    public List<InstrumentItem> findAllInstrumentItemsByBandId(Long bandId) {
        return instrumentRepo.findByBandId(bandId);
    }

    @Override
    public List<InstrumentItem> findInstrumentItemsByMember(Member member) {
        return instrumentRepo.findByAssignedMember(member);
    }

    @Override
    public Optional<InstrumentItem> findInstrumentItemById(Long id) {
        return instrumentRepo.findById(id);
    }

    @Override
    public void deleteInstrumentItem(InstrumentItem item) {
        instrumentRepo.delete(item);
    }

    @Override
    public InventoryOrder saveOrder(InventoryOrder order) {
        return orderRepo.save(order);
    }

    @Override
    public List<InventoryOrder> findAllOrders() {
        return orderRepo.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public List<InventoryOrder> findAllOrdersByBandId(Long bandId) {
        return orderRepo.findByBandId(bandId);
    }

    @Override
    public List<InventoryOrder> findOrdersByMember(Member member) {
        return orderRepo.findByRequesterOrderByCreatedAtDesc(member);
    }

    @Override
    public List<InventoryOrder> findOrdersByStatus(OrderStatus status) {
        return orderRepo.findByStatusOrderByCreatedAtDesc(status);
    }

    @Override
    public Optional<InventoryOrder> findOrderById(Long id) {
        return orderRepo.findById(id);
    }

    @Override
    public AssetAssignmentHistory saveAssignment(AssetAssignmentHistory assignment) {
        return historyRepo.save(assignment);
    }

    @Override
    public void deleteAssignment(AssetAssignmentHistory assignment) {
        historyRepo.delete(assignment);
    }

    @Override
    public List<AssetAssignmentHistory> findHistoryByUniformItem(UniformItem item) {
        return historyRepo.findByUniformItemOrderByAssignedAtDesc(item);
    }

    @Override
    public List<AssetAssignmentHistory> findHistoryByInstrumentItem(InstrumentItem item) {
        return historyRepo.findByInstrumentItemOrderByAssignedAtDesc(item);
    }

    @Override
    public List<AssetAssignmentHistory> findHistoryByMember(Member member) {
        return historyRepo.findByMemberOrderByAssignedAtDesc(member);
    }

    @Override
    public List<AssetAssignmentHistory> findActiveAssignments() {
        return historyRepo.findByActiveTrue();
    }
}
