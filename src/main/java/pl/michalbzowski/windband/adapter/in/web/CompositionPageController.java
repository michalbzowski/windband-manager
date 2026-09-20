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
import pl.michalbzowski.windband.application.command.composition.AddPartCommand;
import pl.michalbzowski.windband.application.command.composition.UpdateCompositionCommand;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.composition.CompositionCommandService;
import pl.michalbzowski.windband.application.command.composition.CreateCompositionCommand;
import pl.michalbzowski.windband.application.query.composition.CompositionQueryService;
import pl.michalbzowski.windband.application.query.composition.ScoreFileListQueryService;
import pl.michalbzowski.windband.application.query.instrument.InstrumentQueryService;
import pl.michalbzowski.windband.application.query.scoreanalysis.ScoreAnalysisQueryService;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionStatus;

@Controller
@RequestMapping("/bands/{bandId}/compositions")
@RequiredArgsConstructor
public class CompositionPageController {

    private final CompositionQueryService queryService;
    private final CompositionCommandService commandService;
    private final ScoreFileListQueryService scoreFileListQueryService;
    private final ScoreAnalysisQueryService analysisQueryService;
    private final InstrumentQueryService instrumentQueryService;

    @GetMapping
    public String list(@PathVariable Long bandId,
                       @AuthenticationPrincipal OidcUser oidcUser,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(name = "title", required = false) String titleFilter,
                       @RequestParam(name = "composer", required = false) String composerFilter,
                       @RequestParam(name = "arranger", required = false) String arrangerFilter,
                       @RequestParam(name = "status", required = false) CompositionStatus statusFilter,
                       @RequestAttribute(name = HtmxRequestInterceptor.HTMX_REQUEST_ATTRIBUTE, required = false) Boolean isHtmx,
                       Model model) {
        requireBandAccess(oidcUser, bandId);
        Pageable pageable = PageRequest.of(page, size);
        boolean hasFilters = (titleFilter != null && !titleFilter.isBlank())
                || (composerFilter != null && !composerFilter.isBlank())
                || (arrangerFilter != null && !arrangerFilter.isBlank())
                || statusFilter != null;
        var compositionsPage = hasFilters
                ? queryService.listByBand(bandId,
                        titleFilter == null || titleFilter.isBlank() ? null : titleFilter.trim(),
                        composerFilter == null || composerFilter.isBlank() ? null : composerFilter.trim(),
                        arrangerFilter == null || arrangerFilter.isBlank() ? null : arrangerFilter.trim(),
                        statusFilter, pageable)
                : queryService.listByBand(bandId, null, pageable);
        model.addAttribute("compositions", compositionsPage.getContent());
        model.addAttribute("page", compositionsPage);
        model.addAttribute("bandId", bandId);
        // Echo the active filters back into the template so the form pre-fills
        // whatever the user already set (GET requests preserve state via query
        // string, and HTMX partial refreshes re-render the same <form>).
        model.addAttribute("filterTitle", titleFilter == null ? "" : titleFilter);
        model.addAttribute("filterComposer", composerFilter == null ? "" : composerFilter);
        model.addAttribute("filterArranger", arrangerFilter == null ? "" : arrangerFilter);
        model.addAttribute("filterStatus", statusFilter == null ? "" : statusFilter.name());
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
    public String delete(@PathVariable Long bandId,
                         @PathVariable Long id,
                         @AuthenticationPrincipal OidcUser oidcUser) {
        requireBandAccess(oidcUser, bandId);
        commandService.deleteComposition(id, bandId);
        return "redirect:/bands/" + bandId + "/compositions";
    }

    /**
     * US-7.1 — "Oznacz głosy na stronach nut" (manual entry): add one {@code CompositionInstrument}
     * part mapping to this composition. The form in {@code compositions/detail.html} collects
     * the instrument id, the role (free-form text), and a page range (from…to inclusive).
     *
     * <p>On success the browser is redirected back to the detail page, where the new row
     * appears in the "Pliki nuty / Oznacz głosy" table (rendered from
     * {@code scoreFileListQueryService.partsFor(id, bandId)}). On Bean-Validation failure
     * the form is re-rendered with a Polish error message — no cross-band or unknown-id
     * paths are exercised here: {@code requireBandAccess} + the service's band-scoped lookup
     * fail-closed (409/400) before any row is touched.</p>
     */
    @PostMapping("/{id}/parts")
    public String addPart(@PathVariable Long bandId, @PathVariable Long id,
                          @AuthenticationPrincipal OidcUser oidcUser,
                          @Valid @ModelAttribute AddPartCommand cmd,
                          BindingResult bindingResult) {
        requireBandAccess(oidcUser, bandId);
        if (bindingResult.hasErrors()) {
            String error = bindingResult.getFieldErrors().stream()
                    .map(fe -> fe.getDefaultMessage())
                    .findFirst()
                    .orElse("Błąd walidacji formularza");
            throw new IllegalArgumentException(error);
        }
        commandService.addPart(id, cmd.getInstrumentId(), cmd.getRole(),
                cmd.getPageFrom(), cmd.getPageTo(), null, bandId);
        return "redirect:/bands/" + bandId + "/compositions/" + id;
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
        var composition = queryService.get(id, bandId);
        // US-4.3 — the "Analizuj utwór" modal needs a stable snapshot of this composition's
        // uploaded score files (one must be picked before the analyze button enables).
        // Reading files() INSIDE the service transaction avoids LazyInitializationException on
        // render after Hibernate closes the session.
        model.addAttribute("scoreFiles", scoreFileListQueryService.listByComposition(id, bandId));
        var latest = analysisQueryService.latestFor(id, bandId);
        model.addAttribute("latestAnalysisId",     latest.map(pl.michalbzowski.windband.application.query.scoreanalysis.ScoreAnalysisQueryService.LatestScoreAnalysisDto::id).orElse(null));
        model.addAttribute("latestPhase",          latest.map(pl.michalbzowski.windband.application.query.scoreanalysis.ScoreAnalysisQueryService.LatestScoreAnalysisDto::phase).orElse(null));
        model.addAttribute("latestRunnerRef",      latest.map(pl.michalbzowski.windband.application.query.scoreanalysis.ScoreAnalysisQueryService.LatestScoreAnalysisDto::runnerRef).orElse(null));
        model.addAttribute("latestErrorMessage",   latest.map(pl.michalbzowski.windband.application.query.scoreanalysis.ScoreAnalysisQueryService.LatestScoreAnalysisDto::errorMessage).orElse(null));
        model.addAttribute("latestArrangementJsonPath",      latest.map(pl.michalbzowski.windband.application.query.scoreanalysis.ScoreAnalysisQueryService.LatestScoreAnalysisDto::arrangementJsonPath).orElse(null));
        model.addAttribute("latestArrangementMusicxmlPath",  latest.map(pl.michalbzowski.windband.application.query.scoreanalysis.ScoreAnalysisQueryService.LatestScoreAnalysisDto::arrangementMusicxmlPath).orElse(null));
        model.addAttribute("latestArrangementMidPath",       latest.map(pl.michalbzowski.windband.application.query.scoreanalysis.ScoreAnalysisQueryService.LatestScoreAnalysisDto::arrangementMidPath).orElse(null));
        model.addAttribute("latestValidationTxtPath",        latest.map(pl.michalbzowski.windband.application.query.scoreanalysis.ScoreAnalysisQueryService.LatestScoreAnalysisDto::validationTxtPath).orElse(null));
        model.addAttribute("latestStartedAt",     latest.map(pl.michalbzowski.windband.application.query.scoreanalysis.ScoreAnalysisQueryService.LatestScoreAnalysisDto::startedAt).orElse(null));
        model.addAttribute("latestFinishedAt",    latest.map(pl.michalbzowski.windband.application.query.scoreanalysis.ScoreAnalysisQueryService.LatestScoreAnalysisDto::finishedAt).orElse(null));
        // US-7.1 — "Oznacz głosy na stronach nut" panel: the mapping of pages → instruments
        // for this composition, plus the band roster's instrument list for the picker dropdown.
        model.addAttribute("parts", scoreFileListQueryService.partsFor(id, bandId));
        model.addAttribute("bandInstruments", instrumentQueryService.findAll(bandId));
        model.addAttribute("composition", composition);
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
