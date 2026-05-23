package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.dto.MemberDto;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;

@Controller
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberPageController {

    private final MemberQueryService memberQueryService;

    @GetMapping
    public String listPage(Model model) {
        model.addAttribute("members", memberQueryService.getAllActiveMembers());
        return "members/list";
    }

    @GetMapping("/new")
    public String newMemberForm(Model model) {
        model.addAttribute("member", emptyMemberDto());
        return "members/form";
    }

    @GetMapping("/{id}/edit")
    public String editMemberForm(@PathVariable Long id, Model model) {
        model.addAttribute("member", memberQueryService.getMemberById(id));
        return "members/form";
    }

    private MemberDto emptyMemberDto() {
        return new MemberDto(null, "", "", null, 0, false, false,
                "", "", "MEMBER", false, true, "", java.util.List.of(), null);
    }
}
