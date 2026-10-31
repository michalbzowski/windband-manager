package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.dto.MemberDto;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.band.MemberAttributeQueryService;
import pl.michalbzowski.windband.application.query.instrument.InstrumentQueryService;
import pl.michalbzowski.windband.application.query.inventory.UnifiedInventoryItemQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.InventoryItem;
import pl.michalbzowski.windband.domain.inventory.ItemType;
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
    private final UnifiedInventoryItemQueryService inventoryQueryService;

    @GetMapping
    public String listPage(@ModelAttribute("activeTeamId") Long activeTeamId, Model model,
                           @RequestParam(required = false) Boolean showInactive,
                           @RequestParam(required = false) Long focus,
                           @RequestParam(required = false) String q,
                           @RequestParam(required = false) String instrument) {
        boolean showingInactiveMembers = Boolean.TRUE.equals(showInactive);

        List<MemberDto> members;
        long inactiveCount = memberQueryService.getInactiveMemberCount(activeTeamId);

        if (showingInactiveMembers) {
            model.addAttribute("active", memberQueryService.getAllActiveMembers(activeTeamId));
            members = memberQueryService.getAllInactiveMembers(activeTeamId);
        } else {
            members = memberQueryService.getAllActiveMembers(activeTeamId);
        }
        List<MemberDto> allMembers = members;
        members = filterMembers(members, q, instrument);

        model.addAttribute("members", members);
        model.addAttribute("showingInactiveMembers", showingInactiveMembers);
        model.addAttribute("showingResignedMembers", showingInactiveMembers); // Backend: inactive, UI display: resigned
        model.addAttribute("inactiveCount", inactiveCount);
        model.addAttribute("resignedCount", inactiveCount);
        model.addAttribute("activeTeamId", activeTeamId);
        model.addAttribute("focusMemberId", focus);
        model.addAttribute("query", q);
        model.addAttribute("instrumentFilter", instrument);
        model.addAttribute("availableInstruments", distinctInstruments(allMembers));
        model.addAttribute("allMembersCount", allMembers.size());
        model.addAttribute("today", LocalDate.now());
        return "members/list";
    }

    @GetMapping("/list")
    public String listFragment(@ModelAttribute("activeTeamId") Long activeTeamId,
                               @RequestParam(required = false) Boolean showInactive,
                               @RequestParam(required = false) Long focus,
                               @RequestParam(required = false) String q,
                               @RequestParam(required = false) String instrument,
                               Model model) {
        boolean showingInactiveMembers = Boolean.TRUE.equals(showInactive);

        List<MemberDto> members;
        long inactiveCount = memberQueryService.getInactiveMemberCount(activeTeamId);

        if (showingInactiveMembers) {
            members = memberQueryService.getAllInactiveMembers(activeTeamId);
        } else {
            members = memberQueryService.getAllActiveMembers(activeTeamId);
        }
        List<MemberDto> allMembers = members;
        members = filterMembers(members, q, instrument);

        model.addAttribute("members", members);
        model.addAttribute("showingInactiveMembers", showingInactiveMembers);
        model.addAttribute("showingResignedMembers", showingInactiveMembers); // Backend: inactive, UI display: resigned
        model.addAttribute("inactiveCount", inactiveCount);
        model.addAttribute("resignedCount", inactiveCount);
        model.addAttribute("activeTeamId", activeTeamId);
        model.addAttribute("focusMemberId", focus);
        model.addAttribute("query", q);
        model.addAttribute("instrumentFilter", instrument);
        model.addAttribute("availableInstruments", distinctInstruments(allMembers));
        model.addAttribute("allMembersCount", allMembers.size());
        model.addAttribute("today", LocalDate.now());
        return "members/list :: #members-content";
    }

    /**
     * Issue #268 — the member list had no way to narrow it down. Filtering happens here rather
     * than in JavaScript so the query survives the HTMX fragment swaps (add member, toggle
     * resigned) and a filtered view stays shareable as {@code /members?q=&instrument=}.
     */
    static List<MemberDto> filterMembers(List<MemberDto> members, String q, String instrument) {
        String needle = (q == null || q.isBlank()) ? null : q.trim().toLowerCase(java.util.Locale.ROOT);
        String section = (instrument == null || instrument.isBlank())
                ? null : instrument.trim().toLowerCase(java.util.Locale.ROOT);
        return members.stream()
                .filter(m -> needle == null || fullName(m).contains(needle)
                        || containsIgnoreCase(m.email(), needle))
                .filter(m -> section == null || containsIgnoreCase(m.primaryInstrument(), section)
                        || containsIgnoreCase(m.instrumentName(), section))
                .toList();
    }

    private static String fullName(MemberDto m) {
        return ((m.firstName() == null ? "" : m.firstName()) + " "
                + (m.lastName() == null ? "" : m.lastName())).toLowerCase(java.util.Locale.ROOT).trim();
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(needle);
    }

    /** Sekcje dostępne w aktualnie pokazanym zbiorze — bez nich lista rozwinięć byłaby na sztywno. */
    private static List<String> distinctInstruments(List<MemberDto> members) {
        return members.stream()
                .map(MemberDto::primaryInstrument)
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
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
        // Unified inventory items assigned to this member (S2 will render per-type panels)
        List<InventoryItem> assigned = inventoryQueryService.getItemsAssignedToMember(id);
        List<InventoryItem> uniformItems = filterByType(assigned, ItemType.UNIFORM).stream()
                .filter(i -> bandIdOf(i, activeTeamId)).toList();
        List<InventoryItem> instrumentItems = filterByType(assigned, ItemType.INSTRUMENT).stream()
                .filter(i -> bandIdOf(i, activeTeamId)).toList();
        List<InventoryItem> awardItems = filterByType(assigned, ItemType.AWARD).stream()
                .filter(i -> bandIdOf(i, activeTeamId)).toList();
        model.addAttribute("uniformItems", uniformItems);
        model.addAttribute("instrumentItems", instrumentItems);
        model.addAttribute("awardItems", awardItems);
        // Award attributes for detail view (S2 fills definitions from unified catalog)
        Band band = bandQueryService.getBandById(activeTeamId);
        if (band != null && !awardItems.isEmpty()) {
            model.addAttribute("awardAttributeDefs", inventoryQueryService.getAttributeDefsForType(activeTeamId, ItemType.AWARD));
            Map<Long, Map<Long, String>> awardAttrValues = new HashMap<>();
            for (InventoryItem item : awardItems) {
                awardAttrValues.put(item.getId(), inventoryQueryService.getAttributeValueMap(item));
            }
            model.addAttribute("awardAttributeValues", awardAttrValues);
        } else {
            model.addAttribute("awardAttributeDefs", List.of());
            model.addAttribute("awardAttributeValues", Map.of());
        }
        model.addAttribute("today", LocalDate.now());
        return "members/detail :: member-detail-content";
    }

    private List<InventoryItem> filterByType(List<InventoryItem> items, ItemType type) {
        if (items == null) return List.of();
        return items.stream().filter(i -> i.getType() == type).toList();
    }

    private boolean bandIdOf(InventoryItem item, Long teamId) {
        if (teamId == null || item == null || item.getBand() == null) return true;
        return item.getBand().getId().equals(teamId);
    }

    private MemberDto emptyMemberDto() {
        return new MemberDto(null, "", "", null, 0, false, false,
                "", "", true, "", List.of(), null, null, null, null, false);
    }
}
