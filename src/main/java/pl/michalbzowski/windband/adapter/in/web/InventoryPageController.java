package pl.michalbzowski.windband.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.dto.InstrumentAttributeDefDto;
import pl.michalbzowski.windband.application.dto.AwardAttributeDefDto;
import pl.michalbzowski.windband.application.dto.InventoryItemDto;
import pl.michalbzowski.windband.application.dto.InventoryOrderDto;
import pl.michalbzowski.windband.application.dto.OrderAttributeDefDto;
import pl.michalbzowski.windband.application.dto.UniformAttributeDefDto;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.inventory.InventoryQueryService;
import pl.michalbzowski.windband.application.query.inventory.InventoryAttributeQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.AwardItem;
import pl.michalbzowski.windband.domain.inventory.InventoryOrder;
import pl.michalbzowski.windband.domain.inventory.InstrumentItem;
import pl.michalbzowski.windband.domain.inventory.OrderStatus;
import pl.michalbzowski.windband.domain.inventory.UniformItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryPageController {

    private final InventoryQueryService inventoryQueryService;
    private final InventoryAttributeQueryService inventoryAttributeQueryService;
    private final MemberQueryService memberQueryService;
    private final BandQueryService bandQueryService;
    private final ObjectMapper objectMapper;

    private Long getActiveTeamId(OidcUser oidcUser) {
        if (oidcUser instanceof WindbandOidcUser wu) {
            return wu.getActiveTeamId();
        }
        return null;
    }

    private Band getActiveBand(OidcUser oidcUser) {
        Long teamId = getActiveTeamId(oidcUser);
        if (teamId == null) {
            return null;
        }
        return bandQueryService.getBandById(teamId);
    }

    @GetMapping
    public String listPage(@AuthenticationPrincipal OidcUser oidcUser, Model model) throws JsonProcessingException {
        Band band = getActiveBand(oidcUser);
        Long teamId = getActiveTeamId(oidcUser);

        // If no team, show empty inventory
        if (band == null) {
            model.addAttribute("uniformItems", List.of());
            model.addAttribute("instrumentItems", List.of());
            model.addAttribute("orders", List.of());
            model.addAttribute("members", List.of());
            model.addAttribute("orderStatuses", OrderStatus.values());
            return "inventory/list";
        }

        // Get attribute definitions
        List<UniformAttributeDefDto> uniformDefs = inventoryAttributeQueryService.getUniformAttributeDefs(band);
        List<InstrumentAttributeDefDto> instrumentDefs = inventoryAttributeQueryService.getInstrumentAttributeDefs(band);
        List<OrderAttributeDefDto> orderDefs = inventoryAttributeQueryService.getOrderAttributeDefs(band);
        List<AwardAttributeDefDto> awardDefs = inventoryAttributeQueryService.getAwardAttributeDefs(band);

        // Get all items filtered by team
        List<UniformItem> uniformItemsEntities = inventoryQueryService.getAllUniformItemsEntities(teamId);
        List<InstrumentItem> instrumentItemsEntities = inventoryQueryService.getAllInstrumentItemsEntities(teamId);
        List<InventoryItemDto> uniformItems = inventoryQueryService.getAllUniformItems(teamId);
        List<InventoryItemDto> instrumentItems = inventoryQueryService.getAllInstrumentItems(teamId);
        List<InventoryItemDto> awardItems = inventoryQueryService.getAllAwardItems(teamId);
        List<AwardItem> awardItemsEntities = inventoryQueryService.getAllAwardItemsEntities(teamId);

        // Build attribute values map: itemId -> {attrId -> value}
        Map<Long, Map<Long, String>> uniformAttrValues = new HashMap<>();
        for (UniformItem item : uniformItemsEntities) {
            uniformAttrValues.put(item.getId(), inventoryAttributeQueryService.getUniformAttributeValues(item));
        }

        Map<Long, Map<Long, String>> instrumentAttrValues = new HashMap<>();
        for (InstrumentItem item : instrumentItemsEntities) {
            instrumentAttrValues.put(item.getId(), inventoryAttributeQueryService.getInstrumentAttributeValues(item));
        }

        // Get orders with attribute values - filtered by team
        List<InventoryOrder> ordersEntities = inventoryQueryService.getAllOrdersEntities(teamId);
        List<InventoryOrderDto> orders = inventoryQueryService.getAllOrders(teamId);
        Map<Long, Map<Long, String>> orderAttrValues = new HashMap<>();
        for (InventoryOrder order : ordersEntities) {
            orderAttrValues.put(order.getId(), inventoryAttributeQueryService.getOrderAttributeValues(order));
        }

        Map<Long, Map<Long, String>> awardAttrValues = new HashMap<>();
        for (AwardItem item : awardItemsEntities) {
            awardAttrValues.put(item.getId(), inventoryAttributeQueryService.getAwardAttributeValues(item));
        }

        model.addAttribute("uniformItems", uniformItems);
        model.addAttribute("instrumentItems", instrumentItems);
        model.addAttribute("orders", orders);
        model.addAttribute("members", memberQueryService.getAllActiveMembers(teamId));
        model.addAttribute("orderStatuses", OrderStatus.values());
        model.addAttribute("uniformAttributeDefs", uniformDefs);
        model.addAttribute("uniformAttributeValues", uniformAttrValues);
        model.addAttribute("instrumentAttributeDefs", instrumentDefs);
        model.addAttribute("instrumentAttributeValues", instrumentAttrValues);
        model.addAttribute("orderAttributeDefs", orderDefs);
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
    public String ordersFragment(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        Long teamId = getActiveTeamId(oidcUser);
        model.addAttribute("orders", inventoryQueryService.getAllOrders(teamId));
        model.addAttribute("members", memberQueryService.getAllActiveMembers(teamId));
        model.addAttribute("orderStatuses", OrderStatus.values());
        return "inventory/list :: #orders-content";
    }

    @GetMapping("/uniforms/fragment")
    public String uniformsFragment(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        Band band = getActiveBand(oidcUser);
        Long teamId = getActiveTeamId(oidcUser);
        
        if (band == null) {
            model.addAttribute("uniformItems", List.of());
            model.addAttribute("uniformAttributeDefs", List.of());
            model.addAttribute("uniformAttributeValues", Map.of());
            model.addAttribute("members", List.of());
            return "inventory/list :: #uniforms-content";
        }

        List<UniformItem> uniformItems = inventoryQueryService.getAllUniformItemsEntities(teamId);
        List<UniformAttributeDefDto> uniformDefs = inventoryAttributeQueryService.getUniformAttributeDefs(band);

        Map<Long, Map<Long, String>> uniformAttrValues = new HashMap<>();
        for (UniformItem item : uniformItems) {
            uniformAttrValues.put(item.getId(), inventoryAttributeQueryService.getUniformAttributeValues(item));
        }

        model.addAttribute("uniformItems", uniformItems);
        model.addAttribute("uniformAttributeDefs", uniformDefs);
        model.addAttribute("uniformAttributeValues", uniformAttrValues);
        model.addAttribute("members", memberQueryService.getAllActiveMembers(teamId));
        return "inventory/list :: #uniforms-content";
    }

    @GetMapping("/instruments/fragment")
    public String instrumentsFragment(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        Band band = getActiveBand(oidcUser);
        Long teamId = getActiveTeamId(oidcUser);
        
        if (band == null) {
            model.addAttribute("instrumentItems", List.of());
            model.addAttribute("instrumentAttributeDefs", List.of());
            model.addAttribute("instrumentAttributeValues", Map.of());
            model.addAttribute("members", List.of());
            return "inventory/list :: #instruments-content";
        }

        List<InstrumentItem> instrumentItems = inventoryQueryService.getAllInstrumentItemsEntities(teamId);
        List<InstrumentAttributeDefDto> instrumentDefs = inventoryAttributeQueryService.getInstrumentAttributeDefs(band);

        Map<Long, Map<Long, String>> instrumentAttrValues = new HashMap<>();
        for (InstrumentItem item : instrumentItems) {
            instrumentAttrValues.put(item.getId(), inventoryAttributeQueryService.getInstrumentAttributeValues(item));
        }

        model.addAttribute("instrumentItems", instrumentItems);
        model.addAttribute("instrumentAttributeDefs", instrumentDefs);
        model.addAttribute("instrumentAttributeValues", instrumentAttrValues);
        model.addAttribute("members", memberQueryService.getAllActiveMembers(teamId));
        return "inventory/list :: #instruments-content";
    }

    @GetMapping("/awards/fragment")
    public String awardsFragment(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        Band band = getActiveBand(oidcUser);
        Long teamId = getActiveTeamId(oidcUser);

        if (band == null) {
            model.addAttribute("awardItems", List.of());
            model.addAttribute("awardAttributeDefs", List.of());
            model.addAttribute("awardAttributeValues", Map.of());
            model.addAttribute("members", List.of());
            return "inventory/list :: #awards-content";
        }

        List<AwardItem> awardItems = inventoryQueryService.getAllAwardItemsEntities(teamId);
        List<AwardAttributeDefDto> awardDefs = inventoryAttributeQueryService.getAwardAttributeDefs(band);

        Map<Long, Map<Long, String>> awardAttrValues = new HashMap<>();
        for (AwardItem item : awardItems) {
            awardAttrValues.put(item.getId(), inventoryAttributeQueryService.getAwardAttributeValues(item));
        }

        model.addAttribute("awardItems", awardItems);
        model.addAttribute("awardAttributeDefs", awardDefs);
        model.addAttribute("awardAttributeValues", awardAttrValues);
        model.addAttribute("members", memberQueryService.getAllActiveMembers(teamId));
        return "inventory/list :: #awards-content";
    }

    @GetMapping("/orders/{id}/history")
    public String orderHistory(@PathVariable Long id, Model model) {
        model.addAttribute("order", inventoryQueryService.getOrderById(id));
        return "inventory/order-detail";
    }
}
