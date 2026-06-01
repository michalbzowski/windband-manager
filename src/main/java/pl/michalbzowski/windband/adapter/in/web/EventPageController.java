package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
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
    public String listPage(Model model) {
        model.addAttribute("events", eventQueryService.getAllEvents());
        return "events/list";
    }

    @GetMapping("/list")
    public String listFragment(Model model) {
        model.addAttribute("events", eventQueryService.getAllEvents());
        return "events/list :: #events-content";
    }

    @GetMapping("/new")
    public String newEventForm(Model model) {
        model.addAttribute("today", LocalDate.now());
        return "events/form";
    }

    @GetMapping("/{id}")
    public String eventDetail(@PathVariable Long id, Model model) {
        model.addAttribute("event", eventQueryService.getEventDetailById(id));
        model.addAttribute("members", memberQueryService.getAllActiveMembers());
        model.addAttribute("instruments", instrumentQueryService.findAll());
        return "events/detail";
    }

    @GetMapping("/{id}/edit")
    public String editEventForm(@PathVariable Long id, Model model) {
        model.addAttribute("event", eventQueryService.getEventDetailById(id));
        return "events/edit";
    }
}
