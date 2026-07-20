package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
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
    public String listPage(@ModelAttribute("activeTeamId") Long activeTeamId, Model model,
                           HttpServletRequest request) {
        model.addAttribute("upcomingRehearsals", rehearsalQueryService.getUpcomingRehearsals(activeTeamId));
        model.addAttribute("pastRehearsals", rehearsalQueryService.getPastRehearsals(activeTeamId));
        if ("true".equals(request.getHeader("HX-Request"))) {
            return "rehearsals/list :: #rehearsals-content";
        }
        return "rehearsals/list";
    }

    @GetMapping("/list")
    public String listFragment(@ModelAttribute("activeTeamId") Long activeTeamId, Model model) {
        model.addAttribute("upcomingRehearsals", rehearsalQueryService.getUpcomingRehearsals(activeTeamId));
        model.addAttribute("pastRehearsals", rehearsalQueryService.getPastRehearsals(activeTeamId));
        return "rehearsals/list :: #rehearsals-content";
    }

    @GetMapping("/new")
    public String newRehearsalForm(Model model, HttpServletRequest request) {
        model.addAttribute("today", LocalDate.now());
        if ("true".equals(request.getHeader("HX-Request"))) {
            return "rehearsals/form :: #rehearsals-content";
        }
        return "rehearsals/form";
    }

    @GetMapping("/{id}")
    public String rehearsalDetail(@PathVariable Long id, @ModelAttribute("activeTeamId") Long activeTeamId, Model model,
                                  HttpServletRequest request) {
        var rehearsal = rehearsalQueryService.getRehearsalById(id);
        model.addAttribute("rehearsal", rehearsal);
        model.addAttribute("members", memberQueryService.getAllActiveMembers(activeTeamId));
        Map<Long, AttendanceStatus> attendanceMap = rehearsal.getAttendances().stream()
                .collect(Collectors.toMap(
                        a -> a.getMember().getId(),
                        a -> a.getStatus()
                ));
        model.addAttribute("attendanceMap", attendanceMap);
        if ("true".equals(request.getHeader("HX-Request"))) {
            return "rehearsals/detail :: #rehearsals-content";
        }
        return "rehearsals/detail";
    }

    @GetMapping("/{id}/edit")
    public String editRehearsalForm(@PathVariable Long id, Model model, HttpServletRequest request) {
        var rehearsal = rehearsalQueryService.getRehearsalById(id);
        model.addAttribute("rehearsal", rehearsal);
        if ("true".equals(request.getHeader("HX-Request"))) {
            return "rehearsals/edit :: #rehearsals-content";
        }
        return "rehearsals/edit";
    }

    @GetMapping("/{id}/notifications")
    public String rehearsalNotifications(@PathVariable Long id, @ModelAttribute("activeTeamId") Long activeTeamId,
                                         Model model, jakarta.servlet.http.HttpServletRequest request) {
        var rehearsal = rehearsalQueryService.getRehearsalById(id);
        model.addAttribute("rehearsal", rehearsal);
        model.addAttribute("activeMembers", memberQueryService.getAllActiveMembers(activeTeamId));
        if ("true".equals(request.getHeader("HX-Request"))) {
            return "rehearsals/notifications :: notifications-content";
        }
        return "rehearsals/notifications";
    }
}
