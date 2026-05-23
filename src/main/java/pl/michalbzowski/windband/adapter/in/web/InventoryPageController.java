package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.domain.inventory.InventoryRepository;

@Controller
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryPageController {

    private final InventoryRepository inventoryRepository;
    private final MemberQueryService memberQueryService;

    @GetMapping
    public String listPage(Model model) {
        model.addAttribute("uniformItems", inventoryRepository.findAllUniformItems());
        model.addAttribute("instrumentItems", inventoryRepository.findAllInstrumentItems());
        model.addAttribute("members", memberQueryService.getAllActiveMembers());
        return "inventory/list";
    }
}
