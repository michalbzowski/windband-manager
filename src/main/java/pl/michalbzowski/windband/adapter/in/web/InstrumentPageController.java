package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.command.member.InstrumentCommandService;
import pl.michalbzowski.windband.domain.member.Instrument;

import java.util.List;

@Controller
@RequestMapping("/instruments")
@RequiredArgsConstructor
public class InstrumentPageController {

    private final InstrumentCommandService instrumentCommandService;

    @GetMapping
    public String listPage(Model model) {
        List<Instrument> instruments = instrumentCommandService.getAllInstruments();
        model.addAttribute("instruments", instruments);
        return "instruments/list";
    }

    @GetMapping("/new")
    public String newInstrumentForm(Model model) {
        model.addAttribute("instrument", new InstrumentForm(null, "", ""));
        return "instruments/form";
    }

    @GetMapping("/{id}/edit")
    public String editInstrumentForm(@PathVariable Long id, Model model) {
        Instrument instrument = instrumentCommandService.getInstrumentById(id);
        model.addAttribute("instrument", new InstrumentForm(instrument.getId(), instrument.getName(), instrument.getDescription()));
        return "instruments/form";
    }

    public record InstrumentForm(Long id, String name, String description) {}
}
