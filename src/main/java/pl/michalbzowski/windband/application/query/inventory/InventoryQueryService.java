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

    // === Items ===

    public List<InventoryItemDto> getAllUniformItems() {
        return inventoryRepository.findAllUniformItems().stream()
                .map(this::toItemDto)
                .toList();
    }

    public List<InventoryItemDto> getAllInstrumentItems() {
        return inventoryRepository.findAllInstrumentItems().stream()
                .map(this::toItemDto)
                .toList();
    }

    public List<InventoryItemDto> getAllItems() {
        List<InventoryItemDto> all = new java.util.ArrayList<>();
        all.addAll(getAllUniformItems());
        all.addAll(getAllInstrumentItems());
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

    public List<InventoryOrderDto> getAllOrders() {
        return inventoryRepository.findAllOrders().stream()
                .map(this::toOrderDto)
                .toList();
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

    public List<AssignmentHistoryDto> getHistoryByUniformItem(Long uniformItemId) {
        UniformItem item = getUniformItemById(uniformItemId);
        return inventoryRepository.findHistoryByUniformItem(item).stream()
                .map(this::toHistoryDto)
                .toList();
    }

    public List<AssignmentHistoryDto> getHistoryByInstrumentItem(Long instrumentItemId) {
        InstrumentItem item = getInstrumentItemById(instrumentItemId);
        return inventoryRepository.findHistoryByInstrumentItem(item).stream()
                .map(this::toHistoryDto)
                .toList();
    }

    public List<AssignmentHistoryDto> getHistoryByMember(Long memberId) {
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        return inventoryRepository.findHistoryByMember(member).stream()
                .map(this::toHistoryDto)
                .toList();
    }

    public List<AssignmentHistoryDto> getActiveAssignments() {
        return inventoryRepository.findActiveAssignments().stream()
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
                order.getItemName(),
                order.getDescription(),
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
                h.getAssignedAt(),
                h.getReturnedAt(),
                h.isActive(),
                h.getNotes()
        );
    }
}
