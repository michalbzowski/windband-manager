package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;

import java.util.List;
import java.util.Map;

/**
 * Order list page (F2 S1 stub). The legacy {@code InventoryOrder} +
 * {@code OrderAttributeDef/Value} model is removed until the unified order
 * flow lands in S2/S3; this controller keeps the routes compiling and returns
 * empty collections for now.
 */
@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderPageController {

    private final MemberQueryService memberQueryService;

    @GetMapping
    public String ordersPage(@ModelAttribute("activeTeamId") Long activeTeamId, Model model) {
        Long teamId = activeTeamId;

        if (teamId == null) {
            model.addAttribute("orders", List.of());
            model.addAttribute("members", List.of());
            model.addAttribute("orderStatuses", List.of());
            model.addAttribute("orderAttributeDefs", List.of());
            model.addAttribute("orderAttributeValues", Map.of());
            return "orders/list";
        }

        // Legacy order model removed in F2 S1 — empty until S2/S3.
        model.addAttribute("orders", List.of());
        model.addAttribute("members", memberQueryService.getAllActiveMembers(teamId));
        model.addAttribute("orderStatuses", List.of());
        model.addAttribute("orderAttributeDefs", List.of());
        model.addAttribute("orderAttributeValues", Map.of());

        return "orders/list";
    }
}
