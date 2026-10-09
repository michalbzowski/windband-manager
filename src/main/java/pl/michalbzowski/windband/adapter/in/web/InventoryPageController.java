package pl.michalbzowski.windband.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.inventory.UnifiedInventoryItemQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.InventoryItem;
import pl.michalbzowski.windband.domain.inventory.ItemAttributeDef;
import pl.michalbzowski.windband.domain.inventory.ItemType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Inventory list page (F2 S1 stub over the unified item model).
 * The legacy UniformItem/InstrumentItem/AwardItem/InventoryOrder views are removed;
 * this controller now serves unified {@link InventoryItem} + {@link ItemAttributeDef}
 * data. Full template/data switch lands in S2/S3.
 */
@Controller
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryPageController {

    private final UnifiedInventoryItemQueryService inventoryQueryService;
    private final MemberQueryService memberQueryService;
    private final BandQueryService bandQueryService;
    private final ObjectMapper objectMapper;

    private Band getActiveBand(Long activeTeamId) {
        if (activeTeamId == null) {
            return null;
        }
        return bandQueryService.getBandById(activeTeamId);
    }

    @GetMapping
    public String listPage(@ModelAttribute("activeTeamId") Long activeTeamId, Model model) throws JsonProcessingException {
        Band band = getActiveBand(activeTeamId);
        Long teamId = activeTeamId;

        // If no team, show empty inventory
        if (band == null) {
            model.addAttribute("uniformItems", List.of());
            model.addAttribute("instrumentItems", List.of());
            model.addAttribute("orders", List.of());
            model.addAttribute("awardItems", List.of());
            model.addAttribute("members", List.of());
            model.addAttribute("orderStatuses", List.of());
            model.addAttribute("uniformAttributeDefs", List.of());
            model.addAttribute("instrumentAttributeDefs", List.of());
            model.addAttribute("orderAttributeDefs", List.of());
            model.addAttribute("awardAttributeDefs", List.of());
            model.addAttribute("uniformAttributeValues", Map.of());
            model.addAttribute("instrumentAttributeValues", Map.of());
            model.addAttribute("orderAttributeValues", Map.of());
            model.addAttribute("awardAttributeValues", Map.of());
            model.addAttribute("today", LocalDate.now());
            return "inventory/list";
        }

        // Unified attribute definitions (global + band-scoped, active)
        List<ItemAttributeDef> uniformDefs = inventoryQueryService.getAttributeDefsForType(teamId, ItemType.UNIFORM);
        List<ItemAttributeDef> instrumentDefs = inventoryQueryService.getAttributeDefsForType(teamId, ItemType.INSTRUMENT);
        List<ItemAttributeDef> awardDefs = inventoryQueryService.getAttributeDefsForType(teamId, ItemType.AWARD);

        // Get all items filtered by team (single unified model)
        List<InventoryItem> uniformItems = inventoryQueryService.getItemsByBandAndType(teamId, ItemType.UNIFORM);
        List<InventoryItem> instrumentItems = inventoryQueryService.getItemsByBandAndType(teamId, ItemType.INSTRUMENT);
        List<InventoryItem> awardItems = inventoryQueryService.getItemsByBandAndType(teamId, ItemType.AWARD);

        // Build attribute values map: itemId -> {attrId -> value}
        Map<Long, Map<Long, String>> uniformAttrValues = new HashMap<>();
        for (InventoryItem item : uniformItems) {
            uniformAttrValues.put(item.getId(), inventoryQueryService.getAttributeValueMap(item));
        }

        Map<Long, Map<Long, String>> instrumentAttrValues = new HashMap<>();
        for (InventoryItem item : instrumentItems) {
            instrumentAttrValues.put(item.getId(), inventoryQueryService.getAttributeValueMap(item));
        }

        Map<Long, Map<Long, String>> awardAttrValues = new HashMap<>();
        for (InventoryItem item : awardItems) {
            awardAttrValues.put(item.getId(), inventoryQueryService.getAttributeValueMap(item));
        }

        // Legacy order model removed in F2 S1 — orders view is emptied out until S2/S3.
        List<Object> orders = List.of();
        Map<Long, Map<Long, String>> orderAttrValues = Map.of();

        model.addAttribute("uniformItems", uniformItems);
        model.addAttribute("instrumentItems", instrumentItems);
        model.addAttribute("orders", orders);
        model.addAttribute("members", memberQueryService.getAllActiveMembers(teamId));
        model.addAttribute("orderStatuses", List.of());
        model.addAttribute("uniformAttributeDefs", uniformDefs);
        model.addAttribute("uniformAttributeValues", uniformAttrValues);
        model.addAttribute("instrumentAttributeDefs", instrumentDefs);
        model.addAttribute("instrumentAttributeValues", instrumentAttrValues);
        model.addAttribute("orderAttributeDefs", List.of());
        model.addAttribute("orderAttributeValues", orderAttrValues);
        model.addAttribute("awardItems", awardItems);
        model.addAttribute("awardAttributeDefs", awardDefs);
        model.addAttribute("awardAttributeValues", awardAttrValues);

        // JSON versions for JavaScript
        String uniformJson = objectMapper.writeValueAsString(uniformDefs);
        String instrumentJson = objectMapper.writeValueAsString(instrumentDefs);
        String awardJson = objectMapper.writeValueAsString(awardDefs);
        model.addAttribute("uniformAttributeDefsJson", uniformJson);
        model.addAttribute("instrumentAttributeDefsJson", instrumentJson);
        model.addAttribute("awardAttributeDefsJson", awardJson);

        return "inventory/list";
    }

    @GetMapping("/orders")
    public String ordersFragment(@ModelAttribute("activeTeamId") Long activeTeamId, Model model) {
        // Legacy order model removed in F2 S1 — fragment is empty until S2/S3.
        model.addAttribute("orders", List.of());
        model.addAttribute("members", memberQueryService.getAllActiveMembers(activeTeamId));
        model.addAttribute("orderStatuses", List.of());
        model.addAttribute("today", LocalDate.now());
        return "inventory/list :: #orders-content";
    }

    @GetMapping("/uniforms/fragment")
    public String uniformsFragment(@ModelAttribute("activeTeamId") Long activeTeamId, Model model) {
        Band band = getActiveBand(activeTeamId);
        Long teamId = activeTeamId;

        if (band == null) {
            model.addAttribute("uniformItems", List.of());
            model.addAttribute("uniformAttributeDefs", List.of());
            model.addAttribute("uniformAttributeValues", Map.of());
            model.addAttribute("members", List.of());
            model.addAttribute("today", LocalDate.now());
            return "inventory/list :: #uniforms-content";
        }

        List<InventoryItem> uniformItems = inventoryQueryService.getItemsByBandAndType(teamId, ItemType.UNIFORM);
        List<ItemAttributeDef> uniformDefs = inventoryQueryService.getAttributeDefsForType(teamId, ItemType.UNIFORM);

        Map<Long, Map<Long, String>> uniformAttrValues = new HashMap<>();
        for (InventoryItem item : uniformItems) {
            uniformAttrValues.put(item.getId(), inventoryQueryService.getAttributeValueMap(item));
        }

        model.addAttribute("uniformItems", uniformItems);
        model.addAttribute("uniformAttributeDefs", uniformDefs);
        model.addAttribute("uniformAttributeValues", uniformAttrValues);
        model.addAttribute("members", memberQueryService.getAllActiveMembers(teamId));
        model.addAttribute("today", LocalDate.now());
        return "inventory/list :: #uniforms-content";
    }

    @GetMapping("/instruments/fragment")
    public String instrumentsFragment(@ModelAttribute("activeTeamId") Long activeTeamId, Model model) {
        Band band = getActiveBand(activeTeamId);
        Long teamId = activeTeamId;

        if (band == null) {
            model.addAttribute("instrumentItems", List.of());
            model.addAttribute("instrumentAttributeDefs", List.of());
            model.addAttribute("instrumentAttributeValues", Map.of());
            model.addAttribute("members", List.of());
            model.addAttribute("today", LocalDate.now());
            return "inventory/list :: #instruments-content";
        }

        List<InventoryItem> instrumentItems = inventoryQueryService.getItemsByBandAndType(teamId, ItemType.INSTRUMENT);
        List<ItemAttributeDef> instrumentDefs = inventoryQueryService.getAttributeDefsForType(teamId, ItemType.INSTRUMENT);

        Map<Long, Map<Long, String>> instrumentAttrValues = new HashMap<>();
        for (InventoryItem item : instrumentItems) {
            instrumentAttrValues.put(item.getId(), inventoryQueryService.getAttributeValueMap(item));
        }

        model.addAttribute("instrumentItems", instrumentItems);
        model.addAttribute("instrumentAttributeDefs", instrumentDefs);
        model.addAttribute("instrumentAttributeValues", instrumentAttrValues);
        model.addAttribute("members", memberQueryService.getAllActiveMembers(teamId));
        model.addAttribute("today", LocalDate.now());
        return "inventory/list :: #instruments-content";
    }

    @GetMapping("/awards/fragment")
    public String awardsFragment(@ModelAttribute("activeTeamId") Long activeTeamId, Model model) {
        Band band = getActiveBand(activeTeamId);
        Long teamId = activeTeamId;

        if (band == null) {
            model.addAttribute("awardItems", List.of());
            model.addAttribute("awardAttributeDefs", List.of());
            model.addAttribute("awardAttributeValues", Map.of());
            model.addAttribute("members", List.of());
            model.addAttribute("today", LocalDate.now());
            return "inventory/list :: #awards-content";
        }

        List<InventoryItem> awardItems = inventoryQueryService.getItemsByBandAndType(teamId, ItemType.AWARD);
        List<ItemAttributeDef> awardDefs = inventoryQueryService.getAttributeDefsForType(teamId, ItemType.AWARD);

        Map<Long, Map<Long, String>> awardAttrValues = new HashMap<>();
        for (InventoryItem item : awardItems) {
            awardAttrValues.put(item.getId(), inventoryQueryService.getAttributeValueMap(item));
        }

        model.addAttribute("awardItems", awardItems);
        model.addAttribute("awardAttributeDefs", awardDefs);
        model.addAttribute("awardAttributeValues", awardAttrValues);
        model.addAttribute("members", memberQueryService.getAllActiveMembers(teamId));
        model.addAttribute("today", LocalDate.now());
        return "inventory/list :: #awards-content";
    }

    @GetMapping("/orders/{id}/history")
    public String orderHistory(@PathVariable Long id, Model model) {
        // Legacy order model removed in F2 S1 — history view is empty until S2/S3.
        model.addAttribute("order", null);
        return "inventory/order-detail";
    }
}
