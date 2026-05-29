package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.application.query.rehearsal.RehearsalQueryService;
import pl.michalbzowski.windband.domain.rehearsal.AttendanceStatus;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/rehearsals")
@RequiredArgsConstructor
public class RehearsalPageController {

    private final RehearsalQueryService rehearsalQueryService;
    private final MemberQueryService memberQueryService;

    @GetMapping
    public String listPage(Model model) {
        model.addAttribute("rehearsals", rehearsalQueryService.getAllRehearsals());
        return "rehearsals/list";
    }

    @GetMapping("/list")
    public String listFragment(Model model) {
        model.addAttribute("rehearsals", rehearsalQueryService.getAllRehearsals());
        return "rehearsals/list :: #rehearsals-content";
    }

    @GetMapping("/new")
    public String newRehearsalForm(Model model) {
        model.addAttribute("today", LocalDate.now());
        return "rehearsals/form";
    }

    @GetMapping("/{id}")
    public String rehearsalDetail(@PathVariable Long id, Model model) {
        var rehearsal = rehearsalQueryService.getRehearsalById(id);
        model.addAttribute("rehearsal", rehearsal);
        model.addAttribute("members", memberQueryService.getAllActiveMembers());
        Map<Long, AttendanceStatus> attendanceMap = rehearsal.getAttendances().stream()
                .collect(Collectors.toMap(
                        a -> a.getMember().getId(),
                        a -> a.getStatus()
                ));
        model.addAttribute("attendanceMap", attendanceMap);
        return "rehearsals/detail";
    }

    @GetMapping("/{id}/edit")
    public String editRehearsalForm(@PathVariable Long id, Model model) {
        var rehearsal = rehearsalQueryService.getRehearsalById(id);
        model.addAttribute("rehearsal", rehearsal);
        return "rehearsals/edit";
    }
}
