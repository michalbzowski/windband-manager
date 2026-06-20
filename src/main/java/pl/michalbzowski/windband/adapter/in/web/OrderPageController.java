package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.inventory.InventoryQueryService;
import pl.michalbzowski.windband.application.query.inventory.InventoryAttributeQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.InventoryOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderPageController {

    private final InventoryQueryService inventoryQueryService;
    private final InventoryAttributeQueryService inventoryAttributeQueryService;
    private final MemberQueryService memberQueryService;
    private final BandQueryService bandQueryService;

    private Long getActiveTeamId(OidcUser oidcUser) {
        if (oidcUser instanceof WindbandOidcUser wu) {
            return wu.getActiveTeamId();
        }
        return null;
    }

    @GetMapping
    public String ordersPage(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        Long teamId = getActiveTeamId(oidcUser);

        if (teamId == null) {
            model.addAttribute("orders", List.of());
            model.addAttribute("members", List.of());
            model.addAttribute("orderStatuses", List.of());
            model.addAttribute("orderAttributeDefs", List.of());
            model.addAttribute("orderAttributeValues", Map.of());
            return "orders/list";
        }

        Band band = bandQueryService.getBandById(teamId);
        List<InventoryOrder> orderEntities = inventoryQueryService.getAllOrdersEntities(teamId);

        Map<Long, Map<Long, String>> orderAttrValues = new HashMap<>();
        orderEntities.forEach(o ->
                orderAttrValues.put(o.getId(), inventoryAttributeQueryService.getOrderAttributeValues(o)));

        model.addAttribute("orders", inventoryQueryService.getAllOrders(teamId));
        model.addAttribute("members", memberQueryService.getAllActiveMembers(teamId));
        model.addAttribute("orderStatuses", pl.michalbzowski.windband.domain.inventory.OrderStatus.values());
        model.addAttribute("orderAttributeDefs", inventoryAttributeQueryService.getOrderAttributeDefs(band));
        model.addAttribute("orderAttributeValues", orderAttrValues);

        return "orders/list";
    }
}
