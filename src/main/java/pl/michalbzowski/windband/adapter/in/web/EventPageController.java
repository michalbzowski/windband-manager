package pl.michalbzowski.windband.adapter.in.web;

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
    public String listPage(@ModelAttribute("activeTeamId") Long activeTeamId, Model model) {
        model.addAttribute("events", eventQueryService.getAllEvents(activeTeamId));
        return "events/list";
    }

    @GetMapping("/list")
    public String listFragment(@ModelAttribute("activeTeamId") Long activeTeamId, Model model) {
        model.addAttribute("events", eventQueryService.getAllEvents(activeTeamId));
        return "events/list :: #events-content";
    }

    @GetMapping("/new")
    public String newEventForm(Model model) {
        model.addAttribute("today", LocalDate.now());
        return "events/form";
    }

    @GetMapping("/{id}")
    public String eventDetail(@PathVariable Long id, @ModelAttribute("activeTeamId") Long activeTeamId, Model model) {
        var eventDetail = eventQueryService.getEventDetailById(id, activeTeamId);
        model.addAttribute("event", eventDetail);
        
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
        return "events/detail";
    }

    @GetMapping("/{id}/edit")
    public String editEventForm(@PathVariable Long id, @ModelAttribute("activeTeamId") Long activeTeamId, Model model) {
        model.addAttribute("event", eventQueryService.getEventDetailById(id, activeTeamId));
        return "events/edit";
    }
}
