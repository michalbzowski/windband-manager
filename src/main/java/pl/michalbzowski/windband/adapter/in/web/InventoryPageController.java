package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.query.inventory.InventoryQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.domain.inventory.OrderStatus;

@Controller
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryPageController {

    private final InventoryQueryService inventoryQueryService;
    private final MemberQueryService memberQueryService;

    @GetMapping
    public String listPage(Model model) {
        model.addAttribute("uniformItems", inventoryQueryService.getAllUniformItems());
        model.addAttribute("instrumentItems", inventoryQueryService.getAllInstrumentItems());
        model.addAttribute("orders", inventoryQueryService.getAllOrders());
        model.addAttribute("members", memberQueryService.getAllActiveMembers());
        model.addAttribute("orderStatuses", OrderStatus.values());
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
        model.addAttribute("uniformItems", inventoryQueryService.getAllUniformItems());
        model.addAttribute("members", memberQueryService.getAllActiveMembers());
        return "inventory/list :: #uniforms-content";
    }

    @GetMapping("/instruments/fragment")
    public String instrumentsFragment(Model model) {
        model.addAttribute("instrumentItems", inventoryQueryService.getAllInstrumentItems());
        model.addAttribute("members", memberQueryService.getAllActiveMembers());
        return "inventory/list :: #instruments-content";
    }

    @GetMapping("/orders/{id}/history")
    public String orderHistory(@PathVariable Long id, Model model) {
        model.addAttribute("order", inventoryQueryService.getOrderById(id));
        return "inventory/order-detail";
    }
}
