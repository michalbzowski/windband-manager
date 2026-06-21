package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.dto.MemberDto;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.band.MemberAttributeQueryService;
import pl.michalbzowski.windband.application.query.instrument.InstrumentQueryService;
import pl.michalbzowski.windband.application.query.inventory.InventoryQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.member.Member;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberPageController {

    private final MemberQueryService memberQueryService;
    private final InstrumentQueryService instrumentQueryService;
    private final BandQueryService bandQueryService;
    private final MemberAttributeQueryService attributeQueryService;
    private final InventoryQueryService inventoryQueryService;

    private Long getActiveTeamId(OidcUser oidcUser) {
        if (oidcUser instanceof WindbandOidcUser wu) {
            return wu.getActiveTeamId();
        }
        return null;
    }

    @GetMapping
    public String listPage(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        Long teamId = getActiveTeamId(oidcUser);
        model.addAttribute("members", memberQueryService.getAllActiveMembers(teamId));
        model.addAttribute("activeTeamId", teamId);
        return "members/list";
    }

    @GetMapping("/list")
    public String listFragment(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        Long teamId = getActiveTeamId(oidcUser);
        model.addAttribute("members", memberQueryService.getAllActiveMembers(teamId));
        model.addAttribute("activeTeamId", teamId);
        return "members/list :: #members-content";
    }

    @GetMapping("/new")
    public String newMemberForm(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        Long teamId = getActiveTeamId(oidcUser);
        if (teamId == null) {
            return "redirect:/register";
        }
        model.addAttribute("member", emptyMemberDto());
        model.addAttribute("todayJoinedDate", LocalDate.now().toString());
        model.addAttribute("tags", instrumentQueryService.findAll());
        Band band = bandQueryService.getBandById(teamId);
        model.addAttribute("attributeDefs", attributeQueryService.getAttributeDefsForBand(band));
        model.addAttribute("attributeValues", Map.of());
        return "members/form";
    }

    @GetMapping("/{id}/edit")
    public String editMemberForm(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser, Model model) {
        Long teamId = getActiveTeamId(oidcUser);
        if (teamId == null) {
            return "redirect:/register";
        }
        MemberDto dto = memberQueryService.getMemberById(id);
        model.addAttribute("member", dto);
        model.addAttribute("tags", instrumentQueryService.findAll());
        Band band = bandQueryService.getBandById(teamId);
        model.addAttribute("attributeDefs", attributeQueryService.getAttributeDefsForBand(band));
        Member member = memberQueryService.getMemberEntityById(id);
        model.addAttribute("attributeValues", attributeQueryService.getAttributeValuesForMember(member));
        return "members/form";
    }

    @GetMapping("/{id}/detail")
    public String memberDetail(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser, Model model) {
        Long teamId = getActiveTeamId(oidcUser);
        if (teamId == null) {
            return "redirect:/register";
        }
        MemberDto dto = memberQueryService.getMemberById(id);
        model.addAttribute("member", dto);
        model.addAttribute("uniformItems", inventoryQueryService.getUniformItemsByMember(id, teamId));
        model.addAttribute("instrumentItems", inventoryQueryService.getInstrumentItemsByMember(id, teamId));
        model.addAttribute("awardItems", inventoryQueryService.getAwardItemsByMember(id, teamId));
        return "members/detail :: member-detail-content";
    }

    private MemberDto emptyMemberDto() {
        return new MemberDto(null, "", "", null, 0, false, false,
                "", "", true, "", List.of(), null, null, null, null);
    }
}
