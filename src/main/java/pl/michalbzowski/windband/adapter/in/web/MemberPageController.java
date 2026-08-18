package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.dto.MemberDto;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.band.MemberAttributeQueryService;
import pl.michalbzowski.windband.application.query.instrument.InstrumentQueryService;
import pl.michalbzowski.windband.application.query.inventory.InventoryQueryService;
import pl.michalbzowski.windband.application.query.inventory.InventoryAttributeQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.AwardItem;
import pl.michalbzowski.windband.domain.member.Member;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Controller
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberPageController {

    private final MemberQueryService memberQueryService;
    private final InstrumentQueryService instrumentQueryService;
    private final BandQueryService bandQueryService;
    private final MemberAttributeQueryService attributeQueryService;
    private final InventoryQueryService inventoryQueryService;
    private final InventoryAttributeQueryService inventoryAttributeQueryService;

    @GetMapping
    public String listPage(@ModelAttribute("activeTeamId") Long activeTeamId, Model model,
                           @RequestParam(required = false) Boolean showInactive,
                           @RequestParam(required = false) String focus) {
        boolean showingInactiveMembers = Boolean.TRUE.equals(showInactive);

        List<MemberDto> members;
        long inactiveCount = memberQueryService.getInactiveMemberCount(activeTeamId);

        if (showingInactiveMembers) {
            model.addAttribute("active", memberQueryService.getAllActiveMembers(activeTeamId));
            members = memberQueryService.getAllInactiveMembers(activeTeamId);
        } else {
            members = memberQueryService.getAllActiveMembers(activeTeamId);
        }

        Long focusMemberId = parseLongSafely(focus);
        model.addAttribute("members", members);
        model.addAttribute("showingInactiveMembers", showingInactiveMembers);
        model.addAttribute("showingResignedMembers", showingInactiveMembers); // Backend: inactive, UI display: resigned
        model.addAttribute("inactiveCount", inactiveCount);
        model.addAttribute("resignedCount", inactiveCount);
        model.addAttribute("activeTeamId", activeTeamId);
        model.addAttribute("focusMemberId", focusMemberId);
        model.addAttribute("today", LocalDate.now());
        return "members/list";
    }

    @GetMapping("/list")
    public String listFragment(@ModelAttribute("activeTeamId") Long activeTeamId,
                               @RequestParam(required = false) Boolean showInactive,
                               @RequestParam(required = false) String focus, Model model) {
        boolean showingInactiveMembers = Boolean.TRUE.equals(showInactive);

        List<MemberDto> members;
        long inactiveCount = memberQueryService.getInactiveMemberCount(activeTeamId);

        if (showingInactiveMembers) {
            members = memberQueryService.getAllInactiveMembers(activeTeamId);
        } else {
            members = memberQueryService.getAllActiveMembers(activeTeamId);
        }

        Long focusMemberId = parseLongSafely(focus);
        model.addAttribute("members", members);
        model.addAttribute("showingInactiveMembers", showingInactiveMembers);
        model.addAttribute("showingResignedMembers", showingInactiveMembers); // Backend: inactive, UI display: resigned
        model.addAttribute("inactiveCount", inactiveCount);
        model.addAttribute("resignedCount", inactiveCount);
        model.addAttribute("activeTeamId", activeTeamId);
        model.addAttribute("focusMemberId", focusMemberId);
        model.addAttribute("today", LocalDate.now());
        return "members/list :: #members-content";
    }

    @GetMapping("/new")
    public String newMemberForm(@ModelAttribute("activeTeamId") Long activeTeamId, Model model,
                                jakarta.servlet.http.HttpServletRequest request) {
        if (activeTeamId == null) {
            return "redirect:/register";
        }
        model.addAttribute("member", emptyMemberDto());
        model.addAttribute("todayJoinedDate", LocalDate.now().toString());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("tags", instrumentQueryService.findAll(activeTeamId));
        Band band = bandQueryService.getBandById(activeTeamId);
        model.addAttribute("attributeDefs", attributeQueryService.getAttributeDefsForBand(band));
        model.addAttribute("attributeValues", Map.of());
        if ("true".equals(request.getHeader("HX-Request"))) {
            return "members/form :: #members-content";
        }
        return "members/form";
    }

    @GetMapping("/{id}/edit")
    public String editMemberForm(@PathVariable Long id, @ModelAttribute("activeTeamId") Long activeTeamId, Model model,
                                 jakarta.servlet.http.HttpServletRequest request) {
        if (activeTeamId == null) {
            return "redirect:/register";
        }
        MemberDto dto = memberQueryService.getMemberById(id);
        model.addAttribute("member", dto);
        model.addAttribute("tags", instrumentQueryService.findAll(activeTeamId));
        model.addAttribute("today", LocalDate.now());
        Band band = bandQueryService.getBandById(activeTeamId);
        model.addAttribute("attributeDefs", attributeQueryService.getAttributeDefsForBand(band));
        Member member = memberQueryService.getMemberEntityById(id);
        model.addAttribute("attributeValues", attributeQueryService.getAttributeValuesForMember(member));
        if ("true".equals(request.getHeader("HX-Request"))) {
            return "members/form :: #members-content";
        }
        return "members/form";
    }

    @GetMapping("/{id}/detail")
    public String memberDetail(@PathVariable Long id, @ModelAttribute("activeTeamId") Long activeTeamId, Model model) {
        if (activeTeamId == null) {
            return "redirect:/register";
        }
        MemberDto dto = memberQueryService.getMemberById(id);
        model.addAttribute("member", dto);
        model.addAttribute("uniformItems", inventoryQueryService.getUniformItemsByMember(id, activeTeamId));
        model.addAttribute("instrumentItems", inventoryQueryService.getInstrumentItemsByMember(id, activeTeamId));
        model.addAttribute("awardItems", inventoryQueryService.getAwardItemsByMember(id, activeTeamId));
        // Award attributes for detail view
        Band band = bandQueryService.getBandById(activeTeamId);
        if (band != null) {
            model.addAttribute("awardAttributeDefs", inventoryAttributeQueryService.getAwardAttributeDefs(band));
            List<AwardItem> awards = inventoryQueryService.getAwardItemsByMember(id, activeTeamId);
            Map<Long, Map<Long, String>> awardAttrValues = new HashMap<>();
            for (AwardItem item : awards) {
                awardAttrValues.put(item.getId(), inventoryAttributeQueryService.getAwardAttributeValues(item));
            }
            model.addAttribute("awardAttributeValues", awardAttrValues);
        }
        model.addAttribute("today", LocalDate.now());
        return "members/detail :: member-detail-content";
    }

    private MemberDto emptyMemberDto() {
        return new MemberDto(null, "", "", null, 0, false, false,
                "", "", true, "", List.of(), null, null, null, null, false);
    }

    /**
     * Safely parses a String to Long, handling "null" literal strings, empty values, and invalid numbers.
     * Returns null for any non-parseable input instead of throwing an exception.
     */
    private Long parseLongSafely(String value) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            // Log silently and return null for invalid numbers
            return null;
        }
    }
}
