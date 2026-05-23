package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.dto.MemberDto;
import pl.michalbzowski.windband.application.query.band.MemberAttributeQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberPageController {

    private final MemberQueryService memberQueryService;
    private final InstrumentRepository instrumentRepository;
    private final BandRepository bandRepository;
    private final MemberAttributeQueryService attributeQueryService;
    private final MemberRepository memberRepository;

    @GetMapping
    public String listPage(Model model) {
        model.addAttribute("members", memberQueryService.getAllActiveMembers());
        return "members/list";
    }

    @GetMapping("/new")
    public String newMemberForm(Model model) {
        model.addAttribute("member", emptyMemberDto());
        model.addAttribute("instruments", instrumentRepository.findAll());
        Band band = bandRepository.findById(1L).orElse(null);
        if (band != null) {
            model.addAttribute("attributeDefs", attributeQueryService.getAttributeDefsForBand(band));
        }
        model.addAttribute("attributeValues", Map.of());
        return "members/form";
    }

    @GetMapping("/{id}/edit")
    public String editMemberForm(@PathVariable Long id, Model model) {
        MemberDto dto = memberQueryService.getMemberById(id);
        model.addAttribute("member", dto);
        model.addAttribute("instruments", instrumentRepository.findAll());
        Band band = bandRepository.findById(1L).orElse(null);
        if (band != null) {
            model.addAttribute("attributeDefs", attributeQueryService.getAttributeDefsForBand(band));
            Member member = memberRepository.findById(id).orElse(null);
            if (member != null) {
                model.addAttribute("attributeValues", attributeQueryService.getAttributeValuesForMember(member));
            }
        }
        return "members/form";
    }

    private MemberDto emptyMemberDto() {
        return new MemberDto(null, "", "", null, 0, false, false,
                "", "", "MEMBER", false, true, "", List.of(), null, null, null);
    }
}
