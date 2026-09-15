package pl.michalbzowski.windband.adapter.in.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
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
    public String list(@PathVariable Long bandId,
                       @AuthenticationPrincipal OidcUser oidcUser,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestAttribute(name = HtmxRequestInterceptor.HTMX_REQUEST_ATTRIBUTE, required = false) Boolean isHtmx,
                       Model model) {
        requireBandAccess(oidcUser, bandId);
        Pageable pageable = PageRequest.of(page, size);
        var compositionsPage = queryService.listByBand(bandId, null, pageable);
        model.addAttribute("compositions", compositionsPage.getContent());
        model.addAttribute("page", compositionsPage);
        model.addAttribute("bandId", bandId);
        if (Boolean.TRUE.equals(isHtmx)) {
            return "compositions/list :: #compositions-content";
        }
        return "compositions/list";
    }

    @GetMapping("/new")
    public String createForm(@PathVariable Long bandId,
                             @AuthenticationPrincipal OidcUser oidcUser,
                             @RequestAttribute(name = HtmxRequestInterceptor.HTMX_REQUEST_ATTRIBUTE, required = false) Boolean isHtmx,
                             Model model) {
        requireBandAccess(oidcUser, bandId);
        prepareForm(model, bandId, new CreateCompositionCommand(), null);
        if (Boolean.TRUE.equals(isHtmx)) {
            return "compositions/form :: #compositions-content";
        }
        return "compositions/form";
    }

    @PostMapping
    public String create(@PathVariable Long bandId,
                         @AuthenticationPrincipal OidcUser oidcUser,
                         @Valid @ModelAttribute CreateCompositionCommand command,
                         BindingResult bindingResult,
                         Model model) {
        requireBandAccess(oidcUser, bandId);
        if (bindingResult.hasErrors()) {
            String error = bindingResult.getFieldErrors().stream()
                    .map(fe -> fe.getDefaultMessage())
                    .findFirst()
                    .orElse("Błąd walidacji formularza");
            prepareForm(model, bandId, command, error);
            return "compositions/form";
        }
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
                         @AuthenticationPrincipal OidcUser oidcUser,
                         @RequestAttribute(name = HtmxRequestInterceptor.HTMX_REQUEST_ATTRIBUTE, required = false) Boolean isHtmx,
                         Model model) {
        requireBandAccess(oidcUser, bandId);
        model.addAttribute("composition", queryService.get(id, bandId));
        model.addAttribute("bandId", bandId);
        if (Boolean.TRUE.equals(isHtmx)) {
            return "compositions/detail :: #compositions-content";
        }
        return "compositions/detail";
    }

    private void prepareForm(Model model, Long bandId, CreateCompositionCommand command, String error) {
        model.addAttribute("command", command);
        model.addAttribute("bandId", bandId);
        model.addAttribute("error", error);
    }

    private String validationMessage(IllegalArgumentException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Tytuł jest wymagany";
        }
        // Backend (commandService) already returns Polish messages for create;
        // domain layer returns English for update — accept both for now.
        return message;
    }

    private void requireBandAccess(OidcUser oidcUser, Long bandId) {
        if (!(oidcUser instanceof WindbandOidcUser wu) || !wu.belongsToTeam(bandId)) {
            throw new IllegalStateException("Użytkownik nie ma dostępu do zespołu o ID: " + bandId);
        }
    }
}
