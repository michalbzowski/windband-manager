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
import pl.michalbzowski.windband.application.command.composition.UpdateCompositionCommand;
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

    // ---- edit form (US-3.4) ---------------------------------------------

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long bandId, @PathVariable Long id,
                           @AuthenticationPrincipal OidcUser oidcUser,
                           Model model) {
        requireBandAccess(oidcUser, bandId);
        Composition composition = queryService.get(id, bandId);
        UpdateCompositionCommand command = new UpdateCompositionCommand();
        command.setTitle(composition.getTitle());
        command.setDescription(composition.getDescription());
        command.setComposer(composition.getComposer());
        command.setArranger(composition.getArranger());
        prepareEditForm(model, bandId, id, command, null);
        return "compositions/edit";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long bandId, @PathVariable Long id,
                         @AuthenticationPrincipal OidcUser oidcUser,
                         @Valid @ModelAttribute UpdateCompositionCommand command,
                         BindingResult bindingResult,
                         Model model) {
        requireBandAccess(oidcUser, bandId);
        if (bindingResult.hasErrors()) {
            String error = firstFieldError(bindingResult);
            prepareEditForm(model, bandId, id, command, error);
            return "compositions/edit";
        }
        try {
            commandService.update(id, command, bandId);
        } catch (IllegalArgumentException exception) {
            // Domain validation on update (e.g. blank title). Re-render form with message.
            prepareEditForm(model, bandId, id, command, validationMessage(exception));
            return "compositions/edit";
        }
        return "redirect:/bands/" + bandId + "/compositions/" + id;
    }

    private void prepareEditForm(Model model, Long bandId, Long compositionId, UpdateCompositionCommand command, String error) {
        model.addAttribute("command", command);
        model.addAttribute("bandId", bandId);
        model.addAttribute("compositionId", compositionId);
        model.addAttribute("error", error);
    }

    // ---- archive / restore / delete (US-3.5) -----------------------------

    /**
     * US-3.5 — archive a DRAFT/READY composition (ARCHIVED → ARCHIVED is idempotent;
     * the domain sets the status field once, so re-clicking is a harmless no-op that
     * still returns 302 to the detail page). Band isolation and ownership are enforced
     * by {@code commandService.archive} via the same {@code requireOwned(id, bandId)}
     * path every other mutating method uses ({@code IllegalStateException} → 409 on
     * cross-band access — fail-closed: no row is read or touched).
     */
    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long bandId,
                          @PathVariable Long id,
                          @AuthenticationPrincipal OidcUser oidcUser) {
        requireBandAccess(oidcUser, bandId);
        commandService.archive(id, bandId);
        return "redirect:/bands/" + bandId + "/compositions/" + id;
    }

    /**
     * US-3.5 — restore an archived composition to DRAFT so the user can re-map parts /
     * run the READY-gate flow again (see {@code Composition#restore()}: the status is set
     * unconditionally to DRAFT, so calling this for a non-archived row is a safe
     * no-op that leaves the row in DRAFT — it does not throw). Cross-band access still
     * fails closed: unknown or foreign ids never resolve through {@code requireOwned}
     * and surface as 409 via {@code IllegalStateException}.
     */
    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long bandId,
                          @PathVariable Long id,
                          @AuthenticationPrincipal OidcUser oidcUser) {
        requireBandAccess(oidcUser, bandId);
        commandService.restore(id, bandId);
        return "redirect:/bands/" + bandId + "/compositions/" + id;
    }

    /**
     * US-3.5 — hard delete the composition and (via V35/V38 FK {@code ON DELETE CASCADE})
     * every dependent row in a single transaction. The confirmation dialog is mandatory
     * on the client side before this endpoint fires (see detail.html) so an accidental
     * keystroke never reaches this method; cross-band access and missing rows both fail
     * closed with {@code IllegalStateException} → 409 without touching any row in another
     * band's space.
     */
    @PostMapping("/{id}/delete")
    public String deleteComposition(@PathVariable Long bandId,
                                    @PathVariable Long id,
                                    @AuthenticationPrincipal OidcUser oidcUser) {
        requireBandAccess(oidcUser, bandId);
        commandService.deleteComposition(id, bandId);
        return "redirect:/bands/" + bandId + "/compositions";
    }

    private String firstFieldError(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage())
                .findFirst()
                .orElse("Błąd walidacji formularza");
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
