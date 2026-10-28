package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.command.member.InstrumentCommandService;
import pl.michalbzowski.windband.application.query.instrument.InstrumentQueryService;
import pl.michalbzowski.windband.domain.member.Instrument;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/tags")
@RequiredArgsConstructor
public class TagPageController {

    private final InstrumentCommandService instrumentCommandService;
    private final InstrumentQueryService instrumentQueryService;

    @GetMapping
    public String listPage(@ModelAttribute("activeTeamId") Long activeTeamId, Model model) {
        List<Instrument> instruments = instrumentQueryService.findAll(activeTeamId);
        model.addAttribute("instruments", instruments);
        model.addAttribute("today", LocalDate.now());
        return "tags/list";
    }

    @GetMapping("/list")
    public String listFragment(@ModelAttribute("activeTeamId") Long activeTeamId,
                               @RequestParam(required = false) Long focus, Model model) {
        List<Instrument> instruments = instrumentQueryService.findAll(activeTeamId);
        model.addAttribute("instruments", instruments);
        model.addAttribute("today", LocalDate.now());
        // Issue #282: same contract as /members/list?focus= — the container tells the shared
        // htmx:afterSwap handler which row just appeared, so the green pulse and the scroll
        // happen after the swap lands instead of racing it from the form.
        model.addAttribute("focusTagId", focus);
        return "tags/list :: #tags-content";
    }

    @GetMapping("/new")
    public String newInstrumentForm(Model model) {
        model.addAttribute("instrument", new InstrumentForm(null, "", "", 0));
        model.addAttribute("today", LocalDate.now());
        return "tags/form";
    }

    @GetMapping("/{id}/edit")
    public String editInstrumentForm(@PathVariable Long id, @ModelAttribute("activeTeamId") Long activeTeamId, Model model) {
        Instrument instrument = instrumentCommandService.getInstrumentById(id, activeTeamId);
        model.addAttribute("instrument", new InstrumentForm(instrument.getId(), instrument.getName(), instrument.getDescription(), instrument.getSortPriority()));
        model.addAttribute("today", LocalDate.now());
        return "tags/form";
    }

    public record InstrumentForm(Long id, String name, String description, Integer sortPriority) {}
}
