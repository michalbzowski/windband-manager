package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pl.michalbzowski.windband.application.command.composition.CompositionCommandService;
import pl.michalbzowski.windband.application.command.composition.CreateCompositionCommand;
import pl.michalbzowski.windband.application.query.composition.CompositionQueryService;
import pl.michalbzowski.windband.domain.composition.Composition;

@Controller
@RequestMapping("/bands/{bandId}/compositions")
@RequiredArgsConstructor
public class CompositionPageController {

    private final CompositionQueryService queryService;
    private final CompositionCommandService commandService;

    @GetMapping
    public String list(@PathVariable Long bandId, Model model, HttpServletRequest request) {
        model.addAttribute("compositions", queryService.listByBand(bandId, null));
        model.addAttribute("bandId", bandId);
        if (isHtmx(request)) {
            return "compositions/list :: #compositions-content";
        }
        return "compositions/list";
    }

    @GetMapping("/new")
    public String createForm(@PathVariable Long bandId, Model model, HttpServletRequest request) {
        prepareForm(model, bandId, new CreateCompositionCommand(), null);
        if (isHtmx(request)) {
            return "compositions/form :: #compositions-content";
        }
        return "compositions/form";
    }

    @PostMapping
    public String create(@PathVariable Long bandId,
                         @ModelAttribute CreateCompositionCommand command,
                         Model model) {
        try {
            Composition composition = commandService.create(command, bandId);
            return "redirect:/bands/" + bandId + "/compositions/" + composition.getId();
        } catch (IllegalArgumentException exception) {
            prepareForm(model, bandId, command, validationMessage(exception));
            return "compositions/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long bandId, @PathVariable Long id,
                         Model model, HttpServletRequest request) {
        model.addAttribute("composition", queryService.get(id, bandId));
        model.addAttribute("bandId", bandId);
        if (isHtmx(request)) {
            return "compositions/detail :: #compositions-content";
        }
        return "compositions/detail";
    }

    private void prepareForm(Model model, Long bandId, CreateCompositionCommand command, String error) {
        model.addAttribute("command", command);
        model.addAttribute("bandId", bandId);
        model.addAttribute("error", error);
    }

    private String validationMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank() || "title required".equals(message)
                || "title must not be blank".equals(message)) {
            return "Tytuł jest wymagany";
        }
        if ("title must be at most 200 characters".equals(message)) {
            return "Tytuł może mieć maksymalnie 200 znaków";
        }
        return message;
    }

    private boolean isHtmx(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader("HX-Request"));
    }
}
