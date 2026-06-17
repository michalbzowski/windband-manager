package pl.michalbzowski.windband.application.command.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.inventory.InventoryItemNotFoundException;
import pl.michalbzowski.windband.application.command.inventory.InventoryOrderNotFoundException;
import pl.michalbzowski.windband.application.command.member.MemberNotFoundException;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.inventory.AwardItem;
import pl.michalbzowski.windband.domain.inventory.AwardItemRepository;
import pl.michalbzowski.windband.domain.inventory.*;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;
import pl.michalbzowski.windband.domain.user.AppUser;
import pl.michalbzowski.windband.domain.user.AppUserRepository;

import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryCommandService {

    private final InventoryRepository inventoryRepository;
    private final MemberRepository memberRepository;
    private final BandRepository bandRepository;
    private final UniformAttributeCommandService uniformAttributeCommandService;
    private final InstrumentAttributeCommandService instrumentAttributeCommandService;
    private final AwardAttributeCommandService awardAttributeCommandService;
    private final AwardItemRepository awardItemRepository;
    private final AppUserRepository appUserRepository;

    private pl.michalbzowski.windband.domain.band.Band getDefaultBand() {
        return bandRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Default band (id=1) not found"));
    }

    private Band getActiveBand() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof WindbandOidcUser wu) {
            Long teamId = wu.getActiveTeamId();
            if (teamId != null) {
                return bandRepository.findById(teamId).orElse(null);
            }
        }
        return null;
    }

    private AppUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        String username = auth.getName();
        return appUserRepository.findByUsername(username).orElse(null);
    }

    // === Place a new order ===

    public InventoryOrder placeUniformOrder(Long memberId, Map<String, String> attributes) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        InventoryOrder order = InventoryOrder.place(member, InventoryOrderType.UNIFORM);
        order.setAttributesJson(mapToAttributesString(attributes));
        return inventoryRepository.saveOrder(order);
    }

    public InventoryOrder placeInstrumentOrder(Long memberId, Map<String, String> attributes) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        InventoryOrder order = InventoryOrder.place(member, InventoryOrderType.INSTRUMENT);
        order.setAttributesJson(mapToAttributesString(attributes));
        return inventoryRepository.saveOrder(order);
    }

    private String mapToAttributesString(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (sb.length() > 0) sb.append(";");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    private Map<String, String> attributesStringToMap(String attributesStr) {
        if (attributesStr == null || attributesStr.isEmpty()) return java.util.Collections.emptyMap();
        Map<String, String> result = new java.util.HashMap<>();
        for (String pair : attributesStr.split(";")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                result.put(kv[0], kv[1]);
            }
        }
        return result;
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
        
        // Auto-copy to inventory with attributes
        Map<String, String> attributes = attributesStringToMap(order.getAttributesJson());
        if (order.getOrderType() == InventoryOrderType.UNIFORM) {
            addUniformItem(order.getRequester().getId(), attributes);
        } else if (order.getOrderType() == InventoryOrderType.INSTRUMENT) {
            addInstrumentItem(order.getRequester().getId(), attributes);
        }
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
        // Item name is derived from order type since itemName was removed
        String itemName = order.getOrderType() == InventoryOrderType.UNIFORM ? "Stroj" : "Instrument";
        UniformItem item = switch (ownershipStatus) {
            case OWNED -> UniformItem.createOwned(itemName, band);
            case BORROWED -> UniformItem.createBorrowed(itemName, band);
            default -> UniformItem.createOwned(itemName, band);
        };
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
        // Item name is derived from order type since itemName was removed
        String itemName = order.getOrderType() == InventoryOrderType.UNIFORM ? "Stroj" : "Instrument";
        InstrumentItem item = switch (ownershipStatus) {
            case OWNED -> InstrumentItem.createOwned(itemName, band);
            case BORROWED -> InstrumentItem.createBorrowed(itemName, band);
            default -> InstrumentItem.createOwned(itemName, band);
        };
        item.updateDetails(brand, serialNumber, null);
        if (order.getOrderNumber() != null) {
            item.setOrderNumber(order.getOrderNumber());
        }
        return inventoryRepository.saveInstrumentItem(item);
    }

    // === Assign item to member (creates history record) ===

    public void assignUniformToMember(Long uniformId, Long memberId, String conditionAtAssign) {
        UniformItem item = getUniformOrThrow(uniformId);
        Member member = getMemberOrThrow(memberId);
        closeActiveAssignment(item);
        item.assignTo(member);
        inventoryRepository.saveUniformItem(item);
        AppUser currentUser = getCurrentUser();
        inventoryRepository.saveAssignment(
                AssetAssignmentHistory.forUniform(item, member, currentUser, conditionAtAssign, null));
    }

    public void assignInstrumentToMember(Long instrumentId, Long memberId, String conditionAtAssign) {
        InstrumentItem item = getInstrumentOrThrow(instrumentId);
        Member member = getMemberOrThrow(memberId);
        closeActiveAssignment(item);
        item.assignTo(member);
        inventoryRepository.saveInstrumentItem(item);
        AppUser currentUser = getCurrentUser();
        inventoryRepository.saveAssignment(
                AssetAssignmentHistory.forInstrument(item, member, currentUser, conditionAtAssign, null));
    }

    // === Return item to stock ===

    public void returnUniform(Long uniformId, String conditionAtReturn, String notes) {
        UniformItem item = getUniformOrThrow(uniformId);
        item.unassign();
        inventoryRepository.saveUniformItem(item);
        closeActiveAssignment(item, conditionAtReturn, notes);
    }

    public void returnInstrument(Long instrumentId, String conditionAtReturn, String notes) {
        InstrumentItem item = getInstrumentOrThrow(instrumentId);
        item.unassign();
        inventoryRepository.saveInstrumentItem(item);
        closeActiveAssignment(item, conditionAtReturn, notes);
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

    public UniformItem addUniformItem(Long memberId, Map<String, String> attributes) {
        var band = getActiveBand();
        if (band == null) {
            throw new IllegalStateException("Cannot create uniform: user has no active team");
        }
        UniformItem item = UniformItem.createOwned(band); // domyślnie własny
        UniformItem saved = inventoryRepository.saveUniformItem(item);

        // Assign to member if provided
        if (memberId != null) {
            var member = memberRepository.findById(memberId).orElse(null);
            if (member != null) {
                item.assignTo(member, LocalDate.now());
                saved = inventoryRepository.saveUniformItem(item);
            }
        }

        // Save attributes if provided
        if (attributes != null && !attributes.isEmpty()) {
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                try {
                    Long attrDefId = Long.parseLong(entry.getKey());
                    uniformAttributeCommandService.setAttributeValue(saved.getId(), attrDefId, entry.getValue());
                } catch (NumberFormatException e) {
                    // Skip invalid attribute IDs
                }
            }
        }
        return saved;
    }

    public InstrumentItem addInstrumentItem(Long memberId, Map<String, String> attributes) {
        var band = getActiveBand();
        if (band == null) {
            throw new IllegalStateException("Cannot create instrument: user has no active team");
        }
        InstrumentItem item = InstrumentItem.createOwned(band);
        InstrumentItem saved = inventoryRepository.saveInstrumentItem(item);

        // Assign to member if provided
        if (memberId != null) {
            var member = memberRepository.findById(memberId).orElse(null);
            if (member != null) {
                item.assignTo(member, LocalDate.now());
                saved = inventoryRepository.saveInstrumentItem(item);
            }
        }

        // Save attributes if provided
        if (attributes != null && !attributes.isEmpty()) {
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                try {
                    Long attrDefId = Long.parseLong(entry.getKey());
                    instrumentAttributeCommandService.setAttributeValue(saved.getId(), attrDefId, entry.getValue());
                } catch (NumberFormatException e) {
                    // Skip invalid attribute IDs
                }
            }
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
                    h.markReturned(null, null);
                    inventoryRepository.saveAssignment(h);
                });
    }

    private void closeActiveAssignment(UniformItem item, String conditionAtReturn, String notes) {
        inventoryRepository.findHistoryByUniformItem(item).stream()
                .filter(AssetAssignmentHistory::isActive)
                .findFirst()
                .ifPresent(h -> {
                    h.markReturned(conditionAtReturn, notes);
                    inventoryRepository.saveAssignment(h);
                });
    }

    private void closeActiveAssignment(InstrumentItem item) {
        inventoryRepository.findHistoryByInstrumentItem(item).stream()
                .filter(AssetAssignmentHistory::isActive)
                .findFirst()
                .ifPresent(h -> {
                    h.markReturned(null, null);
                    inventoryRepository.saveAssignment(h);
                });
    }

    private void closeActiveAssignment(InstrumentItem item, String conditionAtReturn, String notes) {
        inventoryRepository.findHistoryByInstrumentItem(item).stream()
                .filter(AssetAssignmentHistory::isActive)
                .findFirst()
                .ifPresent(h -> {
                    h.markReturned(conditionAtReturn, notes);
                    inventoryRepository.saveAssignment(h);
                });
    }

    // === Award operations ===

    public AwardItem addAwardItem(Long teamId, String name, String description, Long memberId, Map<String, String> attributeValues) {
        Band band = bandRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Band not found: " + teamId));
        AwardItem item = AwardItem.create(name, band);
        item.updateDetails(name, description);
        if (memberId != null) {
            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
            item.assignTo(member);
        }
        AwardItem saved = awardItemRepository.save(item);
        setAwardAttributeValues(saved, attributeValues);
        return saved;
    }

    public void assignAwardToMember(Long awardId, Long memberId) {
        AwardItem item = awardItemRepository.findById(awardId)
                .orElseThrow(() -> new IllegalArgumentException("AwardItem not found: " + awardId));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        item.assignTo(member);
        awardItemRepository.save(item);
    }

    public void returnAward(Long awardId) {
        AwardItem item = awardItemRepository.findById(awardId)
                .orElseThrow(() -> new IllegalArgumentException("AwardItem not found: " + awardId));
        item.unassign();
        awardItemRepository.save(item);
    }

    public void disposeAward(Long awardId) {
        AwardItem item = awardItemRepository.findById(awardId)
                .orElseThrow(() -> new IllegalArgumentException("AwardItem not found: " + awardId));
        if (item.isAssigned()) {
            throw new IllegalStateException("Cannot dispose award that is assigned to a member: " + awardId);
        }
        awardItemRepository.delete(item);
    }

    public void updateAwardAttributes(Long awardId, Map<String, String> attributeValues) {
        AwardItem item = awardItemRepository.findById(awardId)
                .orElseThrow(() -> new IllegalArgumentException("AwardItem not found: " + awardId));
        setAwardAttributeValues(item, attributeValues);
    }

    private void setAwardAttributeValues(AwardItem item, Map<String, String> attributeValues) {
        if (attributeValues == null) return;
        for (Map.Entry<String, String> entry : attributeValues.entrySet()) {
            Long attrDefId = Long.parseLong(entry.getKey());
            awardAttributeCommandService.setAttributeValue(item.getId(), attrDefId, entry.getValue());
        }
    }
}
