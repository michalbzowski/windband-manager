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
                           jakarta.servlet.http.HttpServletRequest request) {

        model.addAttribute("events", eventQueryService.getAllEvents(activeTeamId));

        if ("true".equals(request.getHeader("HX-Request"))) {
            return "events/list :: #events-list-container";
        }
        return "events/list";

    }

    @GetMapping("/list")

    public String listFragment(@ModelAttribute("activeTeamId") Long activeTeamId, Model model) {

        model.addAttribute("events", eventQueryService.getAllEvents(activeTeamId));

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
