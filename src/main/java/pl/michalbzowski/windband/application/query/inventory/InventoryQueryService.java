package pl.michalbzowski.windband.application.query.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.dto.AssignmentHistoryDto;
import pl.michalbzowski.windband.application.dto.InventoryItemDto;
import pl.michalbzowski.windband.application.dto.InventoryOrderDto;
import pl.michalbzowski.windband.application.command.inventory.InventoryItemNotFoundException;
import pl.michalbzowski.windband.application.command.inventory.InventoryOrderNotFoundException;
import pl.michalbzowski.windband.application.command.member.MemberNotFoundException;
import pl.michalbzowski.windband.domain.inventory.*;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryQueryService {

    private final InventoryRepository inventoryRepository;
    private final MemberRepository memberRepository;
    private final AwardQueryService awardQueryService;

    // === Items ===

    public List<InventoryItemDto> getAllUniformItems(Long teamId) {
        if (teamId == null) return List.of();
        return inventoryRepository.findAllUniformItemsByBandId(teamId).stream()
                .map(this::toItemDto)
                .toList();
    }

    public List<UniformItem> getAllUniformItemsEntities(Long teamId) {
        if (teamId == null) return List.of();
        return inventoryRepository.findAllUniformItemsByBandId(teamId);
    }

    public List<InventoryItemDto> getAllInstrumentItems(Long teamId) {
        if (teamId == null) return List.of();
        return inventoryRepository.findAllInstrumentItemsByBandId(teamId).stream()
                .map(this::toItemDto)
                .toList();
    }

    public List<InstrumentItem> getAllInstrumentItemsEntities(Long teamId) {
        if (teamId == null) return List.of();
        return inventoryRepository.findAllInstrumentItemsByBandId(teamId);
    }

    // Legacy methods (no filtering - returns empty for safety)
    public List<InventoryItemDto> getAllUniformItems() {
        return List.of();
    }

    public List<UniformItem> getAllUniformItemsEntities() {
        return List.of();
    }

    public List<InventoryItemDto> getAllInstrumentItems() {
        return List.of();
    }

    public List<InstrumentItem> getAllInstrumentItemsEntities() {
        return List.of();
    }

    public List<InventoryItemDto> getAllItems() {
        List<InventoryItemDto> all = new java.util.ArrayList<>();
        all.addAll(getAllUniformItems(null));
        all.addAll(getAllInstrumentItems(null));
        return all;
    }

    public UniformItem getUniformItemById(Long id) {
        return inventoryRepository.findUniformItemById(id)
                .orElseThrow(() -> new InventoryItemNotFoundException(id));
    }

    public InstrumentItem getInstrumentItemById(Long id) {
        return inventoryRepository.findInstrumentItemById(id)
                .orElseThrow(() -> new InventoryItemNotFoundException(id));
    }

    // === Orders ===

    public List<InventoryOrderDto> getAllOrders(Long teamId) {
        if (teamId == null) return List.of();
        return inventoryRepository.findAllOrdersByBandId(teamId).stream()
                .map(this::toOrderDto)
                .toList();
    }

    public List<InventoryOrder> getAllOrdersEntities(Long teamId) {
        if (teamId == null) return List.of();
        return inventoryRepository.findAllOrdersByBandId(teamId);
    }

    // Legacy methods
    public List<InventoryOrderDto> getAllOrders() {
        return List.of();
    }

    public List<InventoryOrder> getAllOrdersEntities() {
        return List.of();
    }

    public List<InventoryOrderDto> getOrdersByStatus(OrderStatus status) {
        return inventoryRepository.findOrdersByStatus(status).stream()
                .map(this::toOrderDto)
                .toList();
    }

    public InventoryOrder getOrderById(Long id) {
        return inventoryRepository.findOrderById(id)
                .orElseThrow(() -> new InventoryOrderNotFoundException(id));
    }

    // === Assignment history ===

    public List<AssignmentHistoryDto> getHistoryByUniformItem(Long uniformItemId, Long teamId) {
        UniformItem item = getUniformItemById(uniformItemId);
        if (item.getBand() == null || !item.getBand().getId().equals(teamId)) {
            throw new InventoryItemNotFoundException(uniformItemId);
        }
        return inventoryRepository.findHistoryByUniformItem(item).stream()
                .map(this::toHistoryDto)
                .toList();
    }

    public List<AssignmentHistoryDto> getHistoryByInstrumentItem(Long instrumentItemId, Long teamId) {
        InstrumentItem item = getInstrumentItemById(instrumentItemId);
        if (item.getBand() == null || !item.getBand().getId().equals(teamId)) {
            throw new InventoryItemNotFoundException(instrumentItemId);
        }
        return inventoryRepository.findHistoryByInstrumentItem(item).stream()
                .map(this::toHistoryDto)
                .toList();
    }

    public List<AssignmentHistoryDto> getHistoryByMember(Long memberId, Long teamId) {
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        return inventoryRepository.findHistoryByMember(member).stream()
                .filter(h -> {
                    Long itemBandId = h.isForUniform()
                            ? (h.getUniformItem().getBand() != null ? h.getUniformItem().getBand().getId() : null)
                            : (h.getInstrumentItem().getBand() != null ? h.getInstrumentItem().getBand().getId() : null);
                    return itemBandId != null && itemBandId.equals(teamId);
                })
                .map(this::toHistoryDto)
                .toList();
    }

    public List<AssignmentHistoryDto> getActiveAssignments(Long teamId) {
        return inventoryRepository.findActiveAssignments().stream()
                .filter(h -> {
                    Long itemBandId = h.isForUniform()
                            ? (h.getUniformItem().getBand() != null ? h.getUniformItem().getBand().getId() : null)
                            : (h.getInstrumentItem().getBand() != null ? h.getInstrumentItem().getBand().getId() : null);
                    return itemBandId != null && itemBandId.equals(teamId);
                })
                .map(this::toHistoryDto)
                .toList();
    }

    // === Mappers ===

    private InventoryItemDto toItemDto(UniformItem item) {
        return new InventoryItemDto(
                item.getId(), item.getName(), "UNIFORM",
                null, null, item.getDescription(),
                item.getAssignedMember() != null
                        ? item.getAssignedMember().getFirstName() + " " + item.getAssignedMember().getLastName()
                        : null,
                item.getOwnershipStatus().name(),
                item.getLifecycleStatus().name(),
                item.getOrderNumber()
        );
    }

    private InventoryItemDto toItemDto(InstrumentItem item) {
        return new InventoryItemDto(
                item.getId(), item.getName(), "INSTRUMENT",
                item.getBrand(), item.getSerialNumber(), item.getDescription(),
                item.getAssignedMember() != null
                        ? item.getAssignedMember().getFirstName() + " " + item.getAssignedMember().getLastName()
                        : null,
                item.getOwnershipStatus().name(),
                item.getLifecycleStatus().name(),
                item.getOrderNumber()
        );
    }

    private InventoryOrderDto toOrderDto(InventoryOrder order) {
        return new InventoryOrderDto(
                order.getId(),
                order.getRequester().getFirstName() + " " + order.getRequester().getLastName(),
                order.getOrderType().name(),
                order.getStatus().name(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getNotes(),
                order.getOrderNumber()
        );
    }

    private AssignmentHistoryDto toHistoryDto(AssetAssignmentHistory h) {
        return new AssignmentHistoryDto(
                h.getId(),
                h.getItemName(),
                h.isForUniform() ? "UNIFORM" : "INSTRUMENT",
                h.getItemId(),
                h.getMember().getFirstName() + " " + h.getMember().getLastName(),
                h.getAssignedByName(),
                h.getAssignedAt(),
                h.getReturnedAt(),
                h.isActive(),
                h.getConditionAtAssign(),
                h.getConditionAtReturn(),
                h.getNotes()
        );
    }

    // === Awards ===

    public List<InventoryItemDto> getAllAwardItems(Long teamId) {
        if (teamId == null) return List.of();
        return awardQueryService.getAwardItemsForBand(teamId).stream()
                .map(this::toAwardItemDto)
                .toList();
    }

    public List<AwardItem> getAllAwardItemsEntities(Long teamId) {
        if (teamId == null) return List.of();
        return awardQueryService.getAwardItemsForBand(teamId);
    }

    // === Items by member ===

    public List<UniformItem> getUniformItemsByMember(Long memberId, Long teamId) {
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        return inventoryRepository.findUniformItemsByMember(member).stream()
                .filter(item -> item.getBand().getId().equals(teamId))
                .toList();
    }

    public List<InstrumentItem> getInstrumentItemsByMember(Long memberId, Long teamId) {
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        return inventoryRepository.findInstrumentItemsByMember(member).stream()
                .filter(item -> item.getBand().getId().equals(teamId))
                .toList();
    }

    public List<AwardItem> getAwardItemsByMember(Long memberId, Long teamId) {
        memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        return awardQueryService.getAwardItemsForBand(teamId).stream()
                .filter(item -> item.getAssignedMember() != null
                        && item.getAssignedMember().getId().equals(memberId))
                .toList();
    }

    private InventoryItemDto toAwardItemDto(AwardItem item) {
        // Awards don't have a name column anymore - use empty string or first displayInList attribute
        // For now use empty string since it's attribute-based
        return new InventoryItemDto(
                item.getId(), "", "AWARD",
                null, null, item.getDescription(),
                item.getAssignedMember() != null
                        ? item.getAssignedMember().getFirstName() + " " + item.getAssignedMember().getLastName()
                        : null,
                null,
                null,
                item.getOrderNumber()
        );
    }
}
