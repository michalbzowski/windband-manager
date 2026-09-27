package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.composition.AddInstrumentRoleMapCommand;
import pl.michalbzowski.windband.application.command.composition.InstrumentRoleMapCommandService;
import pl.michalbzowski.windband.application.dto.composition.InstrumentRoleMapDto;
import pl.michalbzowski.windband.application.query.composition.InstrumentRoleMapQueryService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * US-7.3 — band-scoped admin UI for {@code instrument_role_map}. Unit-level contract tests in the
 * same shape as {@link AddPartEndpointTest}: real {@link WindbandOidcUser} principals (owned vs
 * foreign band), mocked application services, assertions on the returned view + model attributes.
 */
class InstrumentRoleMapPageControllerTest {

    private InstrumentRoleMapQueryService queryService;
    private InstrumentRoleMapCommandService commandService;
    private InstrumentRoleMapPageController controller;
    private WindbandOidcUser band1User;
    private WindbandOidcUser band2User;
    private Model model = mock(Model.class);

    @BeforeEach
    void setUp() {
        queryService = mock(InstrumentRoleMapQueryService.class);
        commandService = mock(InstrumentRoleMapCommandService.class);
        controller = new InstrumentRoleMapPageController(queryService, commandService);

        band1User = new WindbandOidcUser(
                mock(OidcUser.class), 100L, "user1", "u1@example.com",
                true, false, 1L, "one", "MEMBER", List.of(1L));
        band2User = new WindbandOidcUser(
                mock(OidcUser.class), 101L, "user2", "u2@example.com",
                true, false, 2L, "two", "MEMBER", List.of(2L));
    }

    @Test
    void page_rendersForOwningBandAndPreparesFormModel() {
        var dto = new InstrumentRoleMapDto(7L, "Klarinet B", "Saksofon 1", "na próbę");
        when(queryService.listByBand(1L)).thenReturn(List.of(dto));

        String view = controller.page(1L, band1User, model);

        assertThat(view).isEqualTo("instrument-roles/list");
        org.mockito.Mockito.verify(model).addAttribute("mappings", List.of(dto));
        verify(model).addAttribute("bandId", 1L);
        verify(model).addAttribute(eq("command"), org.mockito.ArgumentMatchers.any(AddInstrumentRoleMapCommand.class));
    }

    @Test
    void page_rejectsForeignBandUser_beforeAnyServiceCall() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                controller.page(1L, band2User, model));

        verify(queryService, never()).listByBand(org.mockito.ArgumentMatchers.anyLong());
        verify(model, never()).addAttribute(eq("mappings"), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void add_persistsFormValues_whenOwningBand() {
        AddInstrumentRoleMapCommand cmd = command("Klarinet B", "Saksofon 1", "notka");

        String view = controller.add(1L, band1User, cmd, noErrors(cmd), model);

        assertThat(view).isEqualTo("instrument-roles/list");
        verify(commandService).addMapping(eq(1L), eq("Klarinet B"), eq("Saksofon 1"), eq("notka"));
    }

    @Test
    void add_blankNoteFromForm_isPassedThroughRaw_toService() {
        // A blank browser field arrives as an empty string. The controller is a thin adapter:
        // it forwards the form value verbatim; the blank→null normalisation is an
        // application-layer rule enforced inside {@code InstrumentRoleMapCommandService}.
        AddInstrumentRoleMapCommand cmd = command("Flet", "Flet 2", "");

        String view = controller.add(1L, band1User, cmd, noErrors(cmd), model);

        assertThat(view).isEqualTo("instrument-roles/list");
        verify(commandService).addMapping(eq(1L), eq("Flet"), eq("Flet 2"), eq(""));
    }

    @Test
    void add_bindingError_reRendersWithErrorAndSkipsPersistence() {
        AddInstrumentRoleMapCommand cmd = command("", "Saksofon 1", null);
        var bindingResult = new BeanPropertyBindingResult(cmd, "command");
        bindingResult.rejectValue("sourceTag", "NotBlank", "Nazwa etykiety instrumentu jest wymagana");

        String view = controller.add(1L, band1User, cmd, bindingResult, model);

        assertThat(view).isEqualTo("instrument-roles/list");
        verify(model).addAttribute("error", "Nazwa etykiety instrumentu jest wymagana");
        verify(commandService, never()).addMapping(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void add_duplicateRowFromService_reRendersWithPolishMessageAndNoSuccess() {
        AddInstrumentRoleMapCommand cmd = command("Kornet", "Trąbka 1", null);
        when(commandService.addMapping(eq(1L), eq("Kornet"), eq("Trąbka 1"), isNull()))
                .thenThrow(new IllegalArgumentException("Mapowanie 'Kornet' → 'Trąbka 1' już istnieje w tym zespole"));

        String view = controller.add(1L, band1User, cmd, noErrors(cmd), model);

        assertThat(view).isEqualTo("instrument-roles/list");
        verify(model).addAttribute("error", "Mapowanie 'Kornet' → 'Trąbka 1' już istnieje w tym zespole");
        verify(model, never()).addAttribute(eq("success"), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void add_rejectsForeignBandUser() {
        AddInstrumentRoleMapCommand cmd = command("Klarinet B", "Saksofon 1", null);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                controller.add(1L, band2User, cmd, noErrors(cmd), model));

        verify(commandService, never()).addMapping(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void delete_ownsBand_delegatesToServiceAndRedirectsToList() {
        String view = controller.delete(1L, 7L, band1User);

        assertThat(view).isEqualTo("redirect:/bands/1/instrument-roles");
        verify(commandService).deleteMapping(1L, 7L);
    }

    @Test
    void delete_crossTeamRow_failsClosedAndNothingIsDeleted() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                controller.delete(1L, 7L, band2User));

        verify(commandService, never()).deleteMapping(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void delete_unknownRow_failsClosed() {
        // Band-1 user deletes a row that belongs to no band — the service throws ISE (409);
        // the controller must not swallow it into a success redirect.
        org.mockito.Mockito.doThrow(new IllegalStateException("Nie znaleziono mapowania o ID 99"))
                .when(commandService).deleteMapping(1L, 99L);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                controller.delete(1L, 99L, band1User));
    }

    private static AddInstrumentRoleMapCommand command(String tag, String role, String description) {
        var cmd = new AddInstrumentRoleMapCommand();
        cmd.setSourceTag(tag);
        cmd.setTargetRolePattern(role);
        cmd.setDescription(description);
        return cmd;
    }

    private static org.springframework.validation.BindingResult noErrors(AddInstrumentRoleMapCommand cmd) {
        var bindingResult = new BeanPropertyBindingResult(cmd, "command");
        if (bindingResult.hasErrors()) {
            throw new IllegalStateException("test binding must start clean");
        }
        return bindingResult;
    }
}
