package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.command.member.InstrumentCommandService;
import pl.michalbzowski.windband.application.query.instrument.InstrumentQueryService;
import pl.michalbzowski.windband.domain.member.Instrument;

import java.util.List;

@Controller
@RequestMapping("/tags")
@RequiredArgsConstructor
public class TagPageController {

    private final InstrumentCommandService instrumentCommandService;
    private final InstrumentQueryService instrumentQueryService;

    @GetMapping
    public String listPage(Model model) {
        List<Instrument> instruments = instrumentQueryService.findAll();
        model.addAttribute("instruments", instruments);
        return "tags/list";
    }

    @GetMapping("/list")
    public String listFragment(Model model) {
        List<Instrument> instruments = instrumentQueryService.findAll();
        model.addAttribute("instruments", instruments);
        return "tags/list :: #tags-content";
    }

    @GetMapping("/new")
    public String newInstrumentForm(Model model) {
        model.addAttribute("instrument", new InstrumentForm(null, "", "", 0));
        return "tags/form";
    }

    @GetMapping("/{id}/edit")
    public String editInstrumentForm(@PathVariable Long id, Model model) {
        Instrument instrument = instrumentCommandService.getInstrumentById(id);
        model.addAttribute("instrument", new InstrumentForm(instrument.getId(), instrument.getName(), instrument.getDescription(), instrument.getSortPriority()));
        return "tags/form";
    }

    public record InstrumentForm(Long id, String name, String description, Integer sortPriority) {}
}
