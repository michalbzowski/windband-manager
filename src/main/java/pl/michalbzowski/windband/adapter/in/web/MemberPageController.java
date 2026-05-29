package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.dto.MemberDto;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.band.MemberAttributeQueryService;
import pl.michalbzowski.windband.application.query.instrument.InstrumentQueryService;
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

    @GetMapping
    public String listPage(Model model) {
        model.addAttribute("members", memberQueryService.getAllActiveMembers());
        return "members/list";
    }

    @GetMapping("/list")
    public String listFragment(Model model) {
        model.addAttribute("members", memberQueryService.getAllActiveMembers());
        return "members/list :: #members-content";
    }

    @GetMapping("/new")
    public String newMemberForm(Model model) {
        model.addAttribute("member", emptyMemberDto());
        model.addAttribute("todayJoinedDate", LocalDate.now().toString());
        model.addAttribute("instruments", instrumentQueryService.findAll());
        Band band = bandQueryService.getDefaultBand();
        model.addAttribute("attributeDefs", attributeQueryService.getAttributeDefsForBand(band));
        model.addAttribute("attributeValues", Map.of());
        return "members/form";
    }

    @GetMapping("/{id}/edit")
    public String editMemberForm(@PathVariable Long id, Model model) {
        MemberDto dto = memberQueryService.getMemberById(id);
        model.addAttribute("member", dto);
        model.addAttribute("instruments", instrumentQueryService.findAll());
        Band band = bandQueryService.getDefaultBand();
        model.addAttribute("attributeDefs", attributeQueryService.getAttributeDefsForBand(band));
        Member member = memberQueryService.getMemberEntityById(id);
        model.addAttribute("attributeValues", attributeQueryService.getAttributeValuesForMember(member));
        return "members/form";
    }

    private MemberDto emptyMemberDto() {
        return new MemberDto(null, "", "", null, 0, false, false,
                "", "", true, "", List.of(), null, null, null, null);
    }
}
