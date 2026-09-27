package pl.michalbzowski.windband.adapter.in.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.composition.AddInstrumentRoleMapCommand;
import pl.michalbzowski.windband.application.command.composition.InstrumentRoleMapCommandService;
import pl.michalbzowski.windband.application.query.composition.InstrumentRoleMapQueryService;

/**
 * Admin UI for {@code instrument_role_map} (US-7.3) — tag→role mappings per band, managed from
 * the app instead of SQL. Band-scoped under {@code /bands/{bandId}/instrument-roles}: the
 * logged-in user must belong to that team ({@link #requireBandAccess}) and every read/write is
 * additionally scoped by the services ({@code getRequiredBand} + band-qualified lookups), so a
 * foreign band's mappings can never be listed, mutated or deleted.
 */
@Controller
@RequestMapping("/bands/{bandId}/instrument-roles")
@RequiredArgsConstructor
public class InstrumentRoleMapPageController {

    private final InstrumentRoleMapQueryService queryService;
    private final InstrumentRoleMapCommandService commandService;

    /** Renders the band's mapping table + the "dodaj mapowanie" form. */
    @GetMapping
    public String page(@PathVariable Long bandId,
                       @AuthenticationPrincipal OidcUser oidcUser,
                       Model model) {
        requireBandAccess(oidcUser, bandId);
        prepareModel(model, bandId, new AddInstrumentRoleMapCommand(), null);
        return "instrument-roles/list";
    }

    /** Adds one tag→role row. Validation / duplicate errors re-render with a Polish message. */
    @PostMapping
    public String add(@PathVariable Long bandId,
                      @AuthenticationPrincipal OidcUser oidcUser,
                      @Valid @ModelAttribute AddInstrumentRoleMapCommand command,
                      BindingResult bindingResult,
                      Model model) {
        requireBandAccess(oidcUser, bandId);
        if (bindingResult.hasErrors()) {
            prepareModel(model, bandId, command, firstFieldError(bindingResult));
            return "instrument-roles/list";
        }
        try {
            commandService.addMapping(bandId, command.getSourceTag(),
                    command.getTargetRolePattern(), command.getDescription());
        } catch (IllegalArgumentException ex) {
            // Duplicate row or blank input from the entity factory — Polish message, re-render.
            prepareModel(model, bandId, command, validationMessage(ex));
            return "instrument-roles/list";
        }
        model.addAttribute("success", "Dodano mapowanie");
        prepareModel(model, bandId, new AddInstrumentRoleMapCommand(), null);
        return "instrument-roles/list";
    }

    /** Removes one row and returns to the list. A cross-team / unknown id fails closed
     *  ({@link IllegalStateException} → HTTP 409 via {@link GlobalExceptionHandler}); nothing is deleted. */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long bandId,
                         @PathVariable("id") Long mappingId,
                         @AuthenticationPrincipal OidcUser oidcUser) {
        requireBandAccess(oidcUser, bandId);
        commandService.deleteMapping(bandId, mappingId);
        return "redirect:/bands/" + bandId + "/instrument-roles";
    }

    private void prepareModel(Model model, Long bandId, AddInstrumentRoleMapCommand command, String error) {
        // listByBand is fail-closed (unknown band → IAE → 400) and DTO-projected, so the
        // template never touches the lazy Band association.
        model.addAttribute("mappings", queryService.listByBand(bandId));
        model.addAttribute("command", command);
        model.addAttribute("bandId", bandId);
        if (error != null) {
            model.addAttribute("error", error);
        }
    }

    private String firstFieldError(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage())
                .findFirst()
                .orElse("Błąd walidacji formularza");
    }

    private String validationMessage(IllegalArgumentException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Nieprawidłowe dane mapowania";
        }
        return message;
    }

    private void requireBandAccess(OidcUser oidcUser, Long bandId) {
        if (!(oidcUser instanceof WindbandOidcUser wu) || !wu.belongsToTeam(bandId)) {
            throw new IllegalStateException("Użytkownik nie ma dostępu do zespołu o ID: " + bandId);
        }
    }
}
