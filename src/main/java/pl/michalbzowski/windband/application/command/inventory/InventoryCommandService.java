package pl.michalbzowski.windband.application.command.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.inventory.InventoryItemNotFoundException;
import pl.michalbzowski.windband.application.command.inventory.InventoryOrderNotFoundException;
import pl.michalbzowski.windband.application.command.member.MemberNotFoundException;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.inventory.*;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryCommandService {

    private final InventoryRepository inventoryRepository;
    private final MemberRepository memberRepository;
    private final BandRepository bandRepository;

    private pl.michalbzowski.windband.domain.band.Band getDefaultBand() {
        return bandRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Default band (id=1) not found"));
    }

    // === Place a new order ===

    public InventoryOrder placeUniformOrder(Long memberId, String itemName, String description) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        InventoryOrder order = InventoryOrder.place(member, itemName, InventoryOrderType.UNIFORM, description);
        return inventoryRepository.saveOrder(order);
    }

    public InventoryOrder placeInstrumentOrder(Long memberId, String itemName, String description) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        InventoryOrder order = InventoryOrder.place(member, itemName, InventoryOrderType.INSTRUMENT, description);
        return inventoryRepository.saveOrder(order);
    }

    // === Advance order through workflow ===

    public void advanceOrderToApproval(Long orderId) {
        InventoryOrder order = getOrderOrThrow(orderId);
        order.advanceToApproval();
        inventoryRepository.saveOrder(order);
    }

    public void advanceOrderToProduction(Long orderId) {
        InventoryOrder order = getOrderOrThrow(orderId);
        order.advanceToProduction();
        inventoryRepository.saveOrder(order);
    }

    public void markOrderShipped(Long orderId) {
        InventoryOrder order = getOrderOrThrow(orderId);
        order.markShipped();
        inventoryRepository.saveOrder(order);
    }

    public void markOrderDelivered(Long orderId) {
        InventoryOrder order = getOrderOrThrow(orderId);
        order.markDelivered();
        if (order.getOrderNumber() == null) {
            order.generateOrderNumber();
        }
        inventoryRepository.saveOrder(order);
    }

    public void cancelOrder(Long orderId) {
        InventoryOrder order = getOrderOrThrow(orderId);
        order.cancel();
        inventoryRepository.saveOrder(order);
    }

    // === Register a delivered order as an inventory item (adds to stock) ===

    public UniformItem registerUniformFromOrder(Long orderId, OwnershipStatus ownershipStatus) {
        InventoryOrder order = getOrderOrThrow(orderId);
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException("Cannot register item from order with status: " + order.getStatus());
        }
        var band = order.getRequester().getBand();
        UniformItem item = switch (ownershipStatus) {
            case OWNED -> UniformItem.createOwned(order.getItemName(), band);
            case BORROWED -> UniformItem.createBorrowed(order.getItemName(), band);
            default -> UniformItem.createOwned(order.getItemName(), band);
        };
        if (order.getDescription() != null) {
            item.updateDescription(order.getDescription());
        }
        if (order.getOrderNumber() != null) {
            item.setOrderNumber(order.getOrderNumber());
        }
        return inventoryRepository.saveUniformItem(item);
    }

    public InstrumentItem registerInstrumentFromOrder(Long orderId, String brand, String serialNumber,
                                                       OwnershipStatus ownershipStatus) {
        InventoryOrder order = getOrderOrThrow(orderId);
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException("Cannot register item from order with status: " + order.getStatus());
        }
        var band = order.getRequester().getBand();
        InstrumentItem item = switch (ownershipStatus) {
            case OWNED -> InstrumentItem.createOwned(order.getItemName(), band);
            case BORROWED -> InstrumentItem.createBorrowed(order.getItemName(), band);
            default -> InstrumentItem.createOwned(order.getItemName(), band);
        };
        item.updateDetails(brand, serialNumber, order.getDescription());
        if (order.getOrderNumber() != null) {
            item.setOrderNumber(order.getOrderNumber());
        }
        return inventoryRepository.saveInstrumentItem(item);
    }

    // === Assign item to member (creates history record) ===

    public void assignUniformToMember(Long uniformId, Long memberId) {
        UniformItem item = getUniformOrThrow(uniformId);
        Member member = getMemberOrThrow(memberId);
        closeActiveAssignment(item);
        item.assignTo(member);
        inventoryRepository.saveUniformItem(item);
        inventoryRepository.saveAssignment(AssetAssignmentHistory.forUniform(item, member, null));
    }

    public void assignInstrumentToMember(Long instrumentId, Long memberId) {
        InstrumentItem item = getInstrumentOrThrow(instrumentId);
        Member member = getMemberOrThrow(memberId);
        closeActiveAssignment(item);
        item.assignTo(member);
        inventoryRepository.saveInstrumentItem(item);
        inventoryRepository.saveAssignment(AssetAssignmentHistory.forInstrument(item, member, null));
    }

    // === Return item to stock ===

    public void returnUniform(Long uniformId, String notes) {
        UniformItem item = getUniformOrThrow(uniformId);
        item.unassign();
        inventoryRepository.saveUniformItem(item);
        closeActiveAssignment(item, notes);
    }

    public void returnInstrument(Long instrumentId, String notes) {
        InstrumentItem item = getInstrumentOrThrow(instrumentId);
        item.unassign();
        inventoryRepository.saveInstrumentItem(item);
        closeActiveAssignment(item, notes);
    }

    // === Retire / Dispose ===

    public void retireUniform(Long uniformId) {
        UniformItem item = getUniformOrThrow(uniformId);
        item.retireFromStock();
        inventoryRepository.saveUniformItem(item);
    }

    public void retireInstrument(Long instrumentId) {
        InstrumentItem item = getInstrumentOrThrow(instrumentId);
        item.retireFromStock();
        inventoryRepository.saveInstrumentItem(item);
    }

    public void disposeUniform(Long uniformId) {
        UniformItem item = getUniformOrThrow(uniformId);
        if (item.isAssigned()) {
            throw new IllegalStateException("Cannot dispose uniform item that is assigned to a member. Return it first.");
        }
        item.dispose();
        inventoryRepository.saveUniformItem(item);
    }

    public void disposeInstrument(Long instrumentId) {
        InstrumentItem item = getInstrumentOrThrow(instrumentId);
        if (item.isAssigned()) {
            throw new IllegalStateException("Cannot dispose instrument that is assigned to a member. Return it first.");
        }
        item.dispose();
        inventoryRepository.saveInstrumentItem(item);
    }

    // === Add existing items directly (without order) ===

    public UniformItem addUniformItem(String name, String description, Long memberId, OwnershipStatus status) {
        var band = getDefaultBand();
        UniformItem item = switch (status) {
            case OWNED -> UniformItem.createOwned(name, band);
            case BORROWED -> UniformItem.createBorrowed(name, band);
            default -> UniformItem.createOwned(name, band);
        };
        if (description != null) item.updateDescription(description);
        UniformItem saved = inventoryRepository.saveUniformItem(item);
        if (memberId != null) {
            assignUniformToMember(saved.getId(), memberId);
        }
        return saved;
    }

    public InstrumentItem addInstrumentItem(String name, String brand, String serialNumber,
                                             String description, Long memberId, OwnershipStatus status) {
        var band = getDefaultBand();
        InstrumentItem item = switch (status) {
            case OWNED -> InstrumentItem.createOwned(name, band);
            case BORROWED -> InstrumentItem.createBorrowed(name, band);
            default -> InstrumentItem.createOwned(name, band);
        };
        item.updateDetails(brand, serialNumber, description);
        InstrumentItem saved = inventoryRepository.saveInstrumentItem(item);
        if (memberId != null) {
            assignInstrumentToMember(saved.getId(), memberId);
        }
        return saved;
    }

    public void updateUniformOwnership(Long itemId, OwnershipStatus status) {
        UniformItem item = getUniformOrThrow(itemId);
        item.updateOwnershipStatus(status);
        inventoryRepository.saveUniformItem(item);
    }

    public void updateInstrumentOwnership(Long itemId, OwnershipStatus status) {
        InstrumentItem item = getInstrumentOrThrow(itemId);
        item.updateOwnershipStatus(status);
        inventoryRepository.saveInstrumentItem(item);
    }

    public void deleteUniformItem(Long itemId) {
        UniformItem item = getUniformOrThrow(itemId);
        if (item.isAssigned()) {
            throw new IllegalStateException("Cannot delete item assigned to a member. Return it first.");
        }
        inventoryRepository.deleteUniformItem(item);
    }

    public void deleteInstrumentItem(Long itemId) {
        InstrumentItem item = getInstrumentOrThrow(itemId);
        if (item.isAssigned()) {
            throw new IllegalStateException("Cannot delete item assigned to a member. Return it first.");
        }
        inventoryRepository.deleteInstrumentItem(item);
    }

    // === Helpers ===

    private InventoryOrder getOrderOrThrow(Long id) {
        return inventoryRepository.findOrderById(id)
                .orElseThrow(() -> new InventoryOrderNotFoundException(id));
    }

    private UniformItem getUniformOrThrow(Long id) {
        return inventoryRepository.findUniformItemById(id)
                .orElseThrow(() -> new InventoryItemNotFoundException(id));
    }

    private InstrumentItem getInstrumentOrThrow(Long id) {
        return inventoryRepository.findInstrumentItemById(id)
                .orElseThrow(() -> new InventoryItemNotFoundException(id));
    }

    private Member getMemberOrThrow(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new MemberNotFoundException(id));
    }

    private void closeActiveAssignment(UniformItem item) {
        inventoryRepository.findHistoryByUniformItem(item).stream()
                .filter(AssetAssignmentHistory::isActive)
                .findFirst()
                .ifPresent(h -> {
                    h.markReturned(null);
                    inventoryRepository.saveAssignment(h);
                });
    }

    private void closeActiveAssignment(UniformItem item, String notes) {
        inventoryRepository.findHistoryByUniformItem(item).stream()
                .filter(AssetAssignmentHistory::isActive)
                .findFirst()
                .ifPresent(h -> {
                    h.markReturned(notes);
                    inventoryRepository.saveAssignment(h);
                });
    }

    private void closeActiveAssignment(InstrumentItem item) {
        inventoryRepository.findHistoryByInstrumentItem(item).stream()
                .filter(AssetAssignmentHistory::isActive)
                .findFirst()
                .ifPresent(h -> {
                    h.markReturned(null);
                    inventoryRepository.saveAssignment(h);
                });
    }

    private void closeActiveAssignment(InstrumentItem item, String notes) {
        inventoryRepository.findHistoryByInstrumentItem(item).stream()
                .filter(AssetAssignmentHistory::isActive)
                .findFirst()
                .ifPresent(h -> {
                    h.markReturned(notes);
                    inventoryRepository.saveAssignment(h);
                });
    }
}
