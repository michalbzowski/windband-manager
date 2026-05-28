package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.dto.InstrumentAttributeDefDto;
import pl.michalbzowski.windband.application.dto.InventoryItemDto;
import pl.michalbzowski.windband.application.dto.InventoryOrderDto;
import pl.michalbzowski.windband.application.dto.OrderAttributeDefDto;
import pl.michalbzowski.windband.application.dto.UniformAttributeDefDto;
import pl.michalbzowski.windband.application.query.inventory.InventoryQueryService;
import pl.michalbzowski.windband.application.query.inventory.InventoryAttributeQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
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
    private final BandRepository bandRepository;

    private Band getDefaultBand() {
        return bandRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Default band (id=1) not found"));
    }

    @GetMapping
    public String listPage(Model model) {
        Band band = getDefaultBand();

        // Get attribute definitions
        List<UniformAttributeDefDto> uniformDefs = inventoryAttributeQueryService.getUniformAttributeDefs(band);
        List<InstrumentAttributeDefDto> instrumentDefs = inventoryAttributeQueryService.getInstrumentAttributeDefs(band);
        List<OrderAttributeDefDto> orderDefs = inventoryAttributeQueryService.getOrderAttributeDefs(band);

        // Get all items - entities for attribute values, DTOs for view
        List<UniformItem> uniformItemsEntities = inventoryQueryService.getAllUniformItemsEntities();
        List<InstrumentItem> instrumentItemsEntities = inventoryQueryService.getAllInstrumentItemsEntities();
        List<InventoryItemDto> uniformItems = inventoryQueryService.getAllUniformItems();
        List<InventoryItemDto> instrumentItems = inventoryQueryService.getAllInstrumentItems();

        // Build attribute values map: itemId -> {attrId -> value}
        Map<Long, Map<Long, String>> uniformAttrValues = new HashMap<>();
        for (UniformItem item : uniformItemsEntities) {
            uniformAttrValues.put(item.getId(), inventoryAttributeQueryService.getUniformAttributeValues(item));
        }

        Map<Long, Map<Long, String>> instrumentAttrValues = new HashMap<>();
        for (InstrumentItem item : instrumentItemsEntities) {
            instrumentAttrValues.put(item.getId(), inventoryAttributeQueryService.getInstrumentAttributeValues(item));
        }

        // Get orders with attribute values - entities for attributes, DTOs for view
        List<InventoryOrder> ordersEntities = inventoryQueryService.getAllOrdersEntities();
        List<InventoryOrderDto> orders = inventoryQueryService.getAllOrders();
        Map<Long, Map<Long, String>> orderAttrValues = new HashMap<>();
        for (InventoryOrder order : ordersEntities) {
            orderAttrValues.put(order.getId(), inventoryAttributeQueryService.getOrderAttributeValues(order));
        }

        model.addAttribute("uniformItems", uniformItems);
        model.addAttribute("instrumentItems", instrumentItems);
        model.addAttribute("orders", orders);
        model.addAttribute("members", memberQueryService.getAllActiveMembers());
        model.addAttribute("orderStatuses", OrderStatus.values());
        model.addAttribute("uniformAttributeDefs", uniformDefs);
        model.addAttribute("uniformAttributeValues", uniformAttrValues);
        model.addAttribute("instrumentAttributeDefs", instrumentDefs);
        model.addAttribute("instrumentAttributeValues", instrumentAttrValues);
        model.addAttribute("orderAttributeDefs", orderDefs);
        model.addAttribute("orderAttributeValues", orderAttrValues);
        return "inventory/list";
    }

    @GetMapping("/orders")
    public String ordersFragment(Model model) {
        model.addAttribute("orders", inventoryQueryService.getAllOrders());
        model.addAttribute("members", memberQueryService.getAllActiveMembers());
        model.addAttribute("orderStatuses", OrderStatus.values());
        return "inventory/list :: #orders-content";
    }

    @GetMapping("/uniforms/fragment")
    public String uniformsFragment(Model model) {
        Band band = getDefaultBand();

        List<UniformItem> uniformItems = inventoryQueryService.getAllUniformItemsEntities();
        List<UniformAttributeDefDto> uniformDefs = inventoryAttributeQueryService.getUniformAttributeDefs(band);

        Map<Long, Map<Long, String>> uniformAttrValues = new HashMap<>();
        for (UniformItem item : uniformItems) {
            uniformAttrValues.put(item.getId(), inventoryAttributeQueryService.getUniformAttributeValues(item));
        }

        model.addAttribute("uniformItems", uniformItems);
        model.addAttribute("uniformAttributeDefs", uniformDefs);
        model.addAttribute("uniformAttributeValues", uniformAttrValues);
        model.addAttribute("members", memberQueryService.getAllActiveMembers());
        return "inventory/list :: #uniforms-content";
    }

    @GetMapping("/instruments/fragment")
    public String instrumentsFragment(Model model) {
        Band band = getDefaultBand();

        List<InstrumentItem> instrumentItems = inventoryQueryService.getAllInstrumentItemsEntities();
        List<InstrumentAttributeDefDto> instrumentDefs = inventoryAttributeQueryService.getInstrumentAttributeDefs(band);

        Map<Long, Map<Long, String>> instrumentAttrValues = new HashMap<>();
        for (InstrumentItem item : instrumentItems) {
            instrumentAttrValues.put(item.getId(), inventoryAttributeQueryService.getInstrumentAttributeValues(item));
        }

        model.addAttribute("instrumentItems", instrumentItems);
        model.addAttribute("instrumentAttributeDefs", instrumentDefs);
        model.addAttribute("instrumentAttributeValues", instrumentAttrValues);
        model.addAttribute("members", memberQueryService.getAllActiveMembers());
        return "inventory/list :: #instruments-content";
    }

    @GetMapping("/orders/{id}/history")
    public String orderHistory(@PathVariable Long id, Model model) {
        model.addAttribute("order", inventoryQueryService.getOrderById(id));
        return "inventory/order-detail";
    }
}
