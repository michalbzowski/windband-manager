package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.composition.AddPartCommand;
import pl.michalbzowski.windband.application.command.composition.CompositionCommandService;
import pl.michalbzowski.windband.application.query.composition.CompositionQueryService;
import pl.michalbzowski.windband.application.query.composition.ScoreFileListQueryService;
import pl.michalbzowski.windband.application.query.instrument.InstrumentQueryService;
import pl.michalbzowski.windband.application.query.scoreanalysis.ScoreAnalysisQueryService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * US-7.1 regression test: the "Oznacz głosy na stronach nut" form (composition detail page)
 * must POST to /bands/{bandId}/compositions/{id}/parts and persist via compositionCommandService.addPart.
 *
 * <p>This regression exists because, without an explicit @PostMapping("/{id}/parts"),
 * the form was 404-ing on a route that the error-fallback mapped to the composition
 * create flow — surfacing a confusing "Tytuł jest wymagany" banner and losing all entered data.
 */
class AddPartEndpointTest {

    private CompositionCommandService commandService;
    private CompositionPageController controller;
    private WindbandOidcUser band1User;
    private WindbandOidcUser band2User;

    @BeforeEach
    void setUp() {
        commandService = mock(CompositionCommandService.class);
        var queryService = mock(CompositionQueryService.class);
        var scoreFiles = mock(ScoreFileListQueryService.class);
        var analysis = mock(ScoreAnalysisQueryService.class);
        var instruments = mock(InstrumentQueryService.class);
        // The production constructor takes: (queryService, commandService, scoreFiles, analysis, instruments).
        controller = new CompositionPageController(queryService, commandService,
                scoreFiles, analysis, instruments);

        band1User = new WindbandOidcUser(
                mock(OidcUser.class), 100L, "user1", "u1@example.com",
                true, false, 1L, "one", "MEMBER", List.of(1L));
        band2User = new WindbandOidcUser(
                mock(OidcUser.class), 101L, "user2", "u2@example.com",
                true, false, 2L, "two", "MEMBER", List.of(2L));
    }

    @Test
    void addPart_persistsAllFormFields_whenOwningBand() {
        // given — form: instrumentId=7, role="Partytura", pageFrom=1, pageTo=22.
        var cmd = new AddPartCommand();
        cmd.setInstrumentId(7L);
        cmd.setRole("Partytura");
        cmd.setPageFrom(1);
        cmd.setPageTo(22);

        // when — owning band (band 1) calls the controller.
        String view = controller.addPart(1L, 42L, band1User, cmd, noError());

        assertThat(view).startsWith("redirect:/bands/1/compositions/42");

        // then — the domain service is invoked with EXACTLY the form values
        // (confidence=null on the manual entry path).
        verify(commandService).addPart(eq(42L), eq(7L), eq("Partytura"), eq(1), eq(22), isNull(), eq(1L));
    }

    @Test
    void addPart_rejectsForeignBandUser() {
        // band-2 user tries /bands/1/compositions/42/parts — requireBandAccess throws 409.
        var cmd = new AddPartCommand();
        cmd.setInstrumentId(7L);
        cmd.setRole("Partytura");
        cmd.setPageFrom(1);
        cmd.setPageTo(22);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                controller.addPart(1L, 42L, band2User, cmd, noError()));

        // No row must be persisted.
        verify(commandService, never()).addPart(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(Integer.class),
                org.mockito.ArgumentMatchers.any(Integer.class),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private static org.springframework.validation.BindingResult noError() {
        // The controller signature requires a BindingResult; the validation branch in
        // the production method is exercised in other tests. A zero-error stub is correct here.
        var br = new org.springframework.validation.BeanPropertyBindingResult(new AddPartCommand(), "cmd");
        assertNoErrors(br);
        return br;
    }

    private static void assertNoErrors(org.springframework.validation.BindingResult br) {
        // sanity — the test is asserting the happy path on this code.
        if (br.hasErrors()) {
            throw new IllegalStateException("binding should have no errors");
        }
    }
}
