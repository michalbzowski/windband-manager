package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.query.member.GroupQueryService;
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
    private final GroupQueryService groupQueryService;

    @GetMapping
    public String listPage(@ModelAttribute("activeTeamId") Long activeTeamId, Model model,
                           HttpServletRequest request,
                           @RequestParam(required = false) Long focus) {
        model.addAttribute("upcomingRehearsals", rehearsalQueryService.getUpcomingRehearsals(activeTeamId));
        model.addAttribute("pastRehearsals", rehearsalQueryService.getPastRehearsals(activeTeamId));
        model.addAttribute("focusRehearsalId", focus);
        if ("true".equals(request.getHeader("HX-Request"))) {
            return "rehearsals/list :: #rehearsals-content";
        }
        return "rehearsals/list";
    }

    @GetMapping("/list")
    public String listFragment(@ModelAttribute("activeTeamId") Long activeTeamId, Model model,
                               @RequestParam(required = false) Long focus) {
        model.addAttribute("upcomingRehearsals", rehearsalQueryService.getUpcomingRehearsals(activeTeamId));
        model.addAttribute("pastRehearsals", rehearsalQueryService.getPastRehearsals(activeTeamId));
        model.addAttribute("focusRehearsalId", focus);
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

    /**
     * Renders the rehearsal detail page. The view needs three things the
     * event detail page also needed:
     * <ul>
     *   <li>{@code members} — every active member of the current band,
     *       minus the ones already invited (so the multi-member invite
     *       modal can show only remaining candidates),</li>
     *   <li>{@code groups} — every group of the current band, used to
     *       render the "Zaproś grupę" modal,</li>
     *   <li>{@code attendanceMap} — pre-computed memberId → status map
     *       so the Thymeleaf template does not need to stream the
     *       attendances list on every select.</li>
     * </ul>
     */
    @GetMapping("/{id}")
    public String rehearsalDetail(@PathVariable Long id, @ModelAttribute("activeTeamId") Long activeTeamId, Model model,
                                  HttpServletRequest request) {
        var rehearsal = rehearsalQueryService.getRehearsalById(id);
        model.addAttribute("rehearsal", rehearsal);

        // IDs of members already invited to this rehearsal (already have an attendance row)
        var invitedMemberIds = rehearsal.getAttendances().stream()
                .map(a -> a.getMember().getId())
                .collect(Collectors.toSet());

        // The attendance table must always show every active member, so a newly invited member
        // immediately appears with NO_RESPONSE and can be updated after reload.
        model.addAttribute("members", memberQueryService.getAllActiveMembers(activeTeamId));

        // The invite modal should only show members that are not invited yet.
        var availableMembers = memberQueryService.getAllActiveMembers(activeTeamId).stream()
                .filter(m -> !invitedMemberIds.contains(m.id()))
                .collect(Collectors.toList());
        model.addAttribute("inviteMembers", availableMembers);

        // All groups of the current band (manual + dynamic). The detail template renders the
        // membership count and the dynamic badge if applicable.
        model.addAttribute("groups", groupQueryService.getAllGroups(activeTeamId));

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
}
