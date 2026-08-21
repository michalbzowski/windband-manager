package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.query.event.EventQueryService;
import pl.michalbzowski.windband.application.query.meeting.MeetingQueryService;
import pl.michalbzowski.windband.application.query.rehearsal.RehearsalQueryService;

import java.time.LocalDate;


@Controller
@RequestMapping("/meetings")
@RequiredArgsConstructor
public class MeetingPageController {

    private final MeetingQueryService meetingQueryService;
    private final EventQueryService eventQueryService;
    private final RehearsalQueryService rehearsalQueryService;

    @GetMapping
    public String listPage(@ModelAttribute("activeTeamId") Long activeTeamId, Model model,
                           HttpServletRequest request,
                           @RequestParam(required = false) Long focus) {
        model.addAttribute("upcomingMeetings", meetingQueryService.getUpcomingMeetings(activeTeamId));
        model.addAttribute("pastMeetings", meetingQueryService.getPastMeetings(activeTeamId));
        model.addAttribute("focusMeetingId", focus);
        model.addAttribute("today", LocalDate.now());
        if ("true".equals(request.getHeader("HX-Request"))) {
            return "meetings/list :: #meetings-content";
        }
        return "meetings/list";
    }

    @GetMapping("/list")
    public String listFragment(@ModelAttribute("activeTeamId") Long activeTeamId, Model model,
                               @RequestParam(required = false) Long focus) {
        model.addAttribute("upcomingMeetings", meetingQueryService.getUpcomingMeetings(activeTeamId));
        model.addAttribute("pastMeetings", meetingQueryService.getPastMeetings(activeTeamId));
        model.addAttribute("focusMeetingId", focus);
        return "meetings/list :: #meetings-content";
    }

    @GetMapping("/new")
    public String newMeetingForm(Model model, HttpServletRequest request) {
        model.addAttribute("today", LocalDate.now());
        if ("true".equals(request.getHeader("HX-Request"))) {
            return "meetings/new :: #meetings-content";
        }
        return "meetings/new";
    }

    /**
     * Unified detail page for meetings (events or rehearsals).
     * Delegates to appropriate controller based on meeting type.
     */
    @GetMapping("/{meetingId}")
    public String meetingDetail(@PathVariable Long meetingId, Model model) {
        // Try to load as rehearsal first
        var rehearsalOptional = rehearsalQueryService.getRehearsalById(meetingId);
        if (rehearsalOptional != null) {
            return "redirect:/meetings/" + meetingId;  // Will be handled by EventPageController via unified view
        }

        // Otherwise try as event
        var eventOptional = eventQueryService.getEventById(meetingId);
        if (eventOptional != null) {
            model.addAttribute("rehearsal", null);
            return "events/detail";  // Delegate to event detail template with proper backUrl
        }

        // Not found - redirect to list
        return "redirect:/meetings?notFound=true";
    }
}

