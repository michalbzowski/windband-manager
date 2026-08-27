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
        model.addAttribute("today", LocalDate.now());
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
        model.addAttribute("today", LocalDate.now());
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
     * Renders the rehearsal detail page. The view shows ONLY the members that
     * were explicitly invited (have an {@code Attendance} row) — a freshly
     * created rehearsal has no rows, just like a freshly created event. The
     * template iterates over {@code invitedMembers} for the attendance table;
     * the multi-member invite modal uses {@code inviteMembers} (the complement
     * — every active member NOT yet invited). Both lists are pre-computed as
     * {@code MemberDto} records in this method so the Thymeleaf renderer does
     * not need to touch lazy associations on the {@code Member} entity outside
     * the query-service transaction.
     */
    @GetMapping("/{id}")
    public String rehearsalDetail(@PathVariable Long id, @ModelAttribute("activeTeamId") Long activeTeamId, Model model,
                                  HttpServletRequest request) {
        var rehearsal = rehearsalQueryService.getRehearsalById(id);
        model.addAttribute("rehearsal", rehearsal);

        // IDs of members already invited to this rehearsal (have an attendance row)
        var invitedMemberIds = rehearsal.getAttendances().stream()
                .map(a -> a.getMember().getId())
                .collect(Collectors.toSet());

        // Fetch every active member once; split it into the two views we need.
        // This is a single DTO projection (no lazy member.instruments access from Thymeleaf).
        var allActiveMembers = memberQueryService.getAllActiveMembers(activeTeamId);
        var invitedMembers = allActiveMembers.stream()
                .filter(m -> invitedMemberIds.contains(m.id()))
                .collect(Collectors.toList());
        var availableMembers = allActiveMembers.stream()
                .filter(m -> !invitedMemberIds.contains(m.id()))
                .collect(Collectors.toList());
        model.addAttribute("invitedMembers", invitedMembers);
        model.addAttribute("inviteMembers", availableMembers);

        // All groups of the current band (manual + dynamic). The detail template renders the
        // membership count and the dynamic badge if applicable.
        model.addAttribute("groups", groupQueryService.getAllGroups(activeTeamId));

        // Attendance map (memberId -> status) for the status <select> defaults.
        Map<Long, AttendanceStatus> attendanceMap = rehearsal.getAttendances().stream()
                .collect(Collectors.toMap(
                        a -> a.getMember().getId(),
                        a -> a.getStatus()
                ));
        model.addAttribute("attendanceMap", attendanceMap);

        // Attendance status counts for the filter badges
        long presentCount = attendanceMap.values().stream().filter(s -> s == AttendanceStatus.PRESENT).count();
        long excusedCount = attendanceMap.values().stream().filter(s -> s == AttendanceStatus.EXCUSED).count();
        long unexcusedCount = attendanceMap.values().stream().filter(s -> s == AttendanceStatus.UNEXCUSED).count();
        long noResponseCount = attendanceMap.values().stream().filter(s -> s == AttendanceStatus.NO_RESPONSE).count();
        model.addAttribute("presentCount", presentCount);
        model.addAttribute("excusedCount", excusedCount);
        model.addAttribute("unexcusedCount", unexcusedCount);
        model.addAttribute("noResponseCount", noResponseCount);

        // Determine back URL from Referer header, default to rehearsals list
        String referer = request.getHeader("Referer");
        String backUrl = "/rehearsals"; // always defaults to the list view
        if (referer != null) {
            try {
                java.net.URL refUrl = new java.net.URL(referer);
                String path = refUrl.getPath();
                // Map referer paths to their respective list views:
                // - "/" → root stays as root only for dashboard/home context
                // - "/events" or "/events/..." → back to events list
                // - "/rehearsals" or "/rehearsals/..." → back to rehearsals list
                if (path.equals("/")) {
                    backUrl = path;  // root home page
                } else if (path.startsWith("/events")) {
                    backUrl = "/events";
                } else if (path.startsWith("/rehearsals")) {
                    backUrl = "/rehearsals";
                }
            } catch (Exception ignored) {
                // Invalid referer, use default list view
            }
        }
        model.addAttribute("backUrl", backUrl);

        if ("true".equals(request.getHeader("HX-Request"))) {
            return "rehearsals/detail :: #rehearsals-content";
        }
        return "rehearsals/detail";
    }

    @GetMapping("/{id}/edit")
    public String editRehearsalForm(@PathVariable Long id, Model model, HttpServletRequest request) {
        var rehearsal = rehearsalQueryService.getRehearsalById(id);
        model.addAttribute("rehearsal", rehearsal);
        // BACK-URL FIX 2026-08-27: same as EventPageController — pre-resolve the
        // detail URL in Java rather than relying on @{/rehearsals/{rehearsalId}}
        // inside the Thymeleaf fragment (which would render as a literal).
        model.addAttribute("editBackUrl", "/rehearsals/" + id);
        if ("true".equals(request.getHeader("HX-Request"))) {
            return "rehearsals/edit :: #rehearsals-content";
        }
        return "rehearsals/edit";
    }
}
