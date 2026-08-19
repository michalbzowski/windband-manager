package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;

import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.*;

import pl.michalbzowski.windband.application.dto.EventDetailDto.ParticipationDto;

import pl.michalbzowski.windband.application.query.event.EventQueryService;

import pl.michalbzowski.windband.application.query.member.MemberQueryService;

import pl.michalbzowski.windband.application.query.instrument.InstrumentQueryService;

import java.time.LocalDate;

@Controller

@RequestMapping("/events")

@RequiredArgsConstructor

public class EventPageController {

    private final EventQueryService eventQueryService;

    private final MemberQueryService memberQueryService;

    private final InstrumentQueryService instrumentQueryService;

    @GetMapping

    public String listPage(@ModelAttribute("activeTeamId") Long activeTeamId, Model model,
                           jakarta.servlet.http.HttpServletRequest request,
                           @RequestParam(required = false) Long focus) {

        model.addAttribute("upcomingEvents", eventQueryService.getUpcomingEvents(activeTeamId));
        model.addAttribute("pastEvents", eventQueryService.getPastEvents(activeTeamId));
        model.addAttribute("focusEventId", focus);
        model.addAttribute("today", LocalDate.now());

        if ("true".equals(request.getHeader("HX-Request"))) {
            return "events/list :: #events-list-container";
        }
        return "events/list";

    }

    @GetMapping("/list")

    public String listFragment(@ModelAttribute("activeTeamId") Long activeTeamId, Model model,
                               @RequestParam(required = false) Long focus) {

        model.addAttribute("upcomingEvents", eventQueryService.getUpcomingEvents(activeTeamId));
        model.addAttribute("pastEvents", eventQueryService.getPastEvents(activeTeamId));
        model.addAttribute("focusEventId", focus);
        model.addAttribute("today", LocalDate.now());

        return "events/list :: #events-list-container";

    }

    @GetMapping("/new")

    public String newEventForm(Model model, jakarta.servlet.http.HttpServletRequest request) {

        model.addAttribute("today", LocalDate.now());

        if ("true".equals(request.getHeader("HX-Request"))) {
            return "events/form :: #events-list-container";
        }
        return "events/form";

    }

    @GetMapping("/{id}")

    public String eventDetail(@PathVariable Long id, @ModelAttribute("activeTeamId") Long activeTeamId, Model model, HttpServletRequest request) {
        var eventDetail = eventQueryService.getEventDetailById(id, activeTeamId);

        // Computed display properties to avoid complex SpEL in template
        String paymentTypeDisplay;
        if ("FREE".equals(eventDetail.paymentType())) {
            paymentTypeDisplay = "💰 Granie bezpłatne";
        } else if ("PAID_SPLIT".equals(eventDetail.paymentType())) {
            paymentTypeDisplay = "💰 Płatne — podział między grających";
        } else {
            paymentTypeDisplay = "💰 Płatne — na konto zespołu";
        }
        model.addAttribute("paymentTypeDisplay", paymentTypeDisplay);

        // Check if event is in the past (date <= today means already occurred)
        boolean eventAlreadyOccurred = java.time.LocalDate.now().compareTo(eventDetail.date()) >= 0;
        model.addAttribute("eventAlreadyOccurred", eventAlreadyOccurred);

        if (eventDetail.payoutPerMember() != null && "PAID_SPLIT".equals(eventDetail.paymentType())) {
            model.addAttribute("payoutPerMemberFormatted",
                java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("pl")).format(eventDetail.payoutPerMember()));
        }

        // Get IDs of already invited members
        var invitedMemberIds = eventDetail.participations().stream()
                .map(ParticipationDto::memberId)
                .collect(java.util.stream.Collectors.toSet());

        // Filter out already invited members
        var availableMembers = memberQueryService.getAllActiveMembers(activeTeamId).stream()
                .filter(m -> !invitedMemberIds.contains(m.id()))
                .collect(java.util.stream.Collectors.toList());

        model.addAttribute("members", availableMembers);
        model.addAttribute("instruments", instrumentQueryService.findAll(activeTeamId));
        model.addAttribute("event", eventDetail);

        // Determine back URL from Referer header, default to events list
        String referer = request.getHeader("Referer");
        String backUrl = "/events"; // always defaults to the list view
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

        boolean isHtmx = "true".equalsIgnoreCase(request.getHeader("HX-Request"));
        if (isHtmx) {
            return "events/detail :: event-detail-content";
        } else {
            return "events/detail";
        }
    }

    @GetMapping("/{id}/edit")

    public String editEventForm(@PathVariable Long id, @ModelAttribute("activeTeamId") Long activeTeamId, Model model,
                                jakarta.servlet.http.HttpServletRequest request) {

        model.addAttribute("event", eventQueryService.getEventDetailById(id, activeTeamId));

        if ("true".equals(request.getHeader("HX-Request"))) {
            return "events/edit :: #events-list-container";
        }
        return "events/edit";

    }

}