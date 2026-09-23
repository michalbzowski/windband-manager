package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;
import pl.michalbzowski.windband.application.command.composition.PartShareByEmailCommandService;
import pl.michalbzowski.windband.application.command.composition.PartShareTokenCommandService;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.composition.PartLinkQueryService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-7.11 — authenticated share surface. The old whole-PDF {@code GET .../parts/{id}} is GONE
 * (enumerable + leaked the full score); what remains is the token mint/rotate endpoints and the
 * e-mail send. Band-isolation semantics (400 / 409) moved to {@code requireOpenable}.
 */
@ExtendWith(MockitoExtension.class)
class PartLinkRestControllerTest {

    private MockMvc mvc;
    private PartLinkQueryService partLinkQuery;
    private PartShareByEmailCommandService shareByEmail;
    private PartShareTokenCommandService tokenService;
    private BandQueryService bandQueryService;

    @BeforeEach
    void setUp() {
        partLinkQuery = mock(PartLinkQueryService.class);
        shareByEmail  = mock(PartShareByEmailCommandService.class);
        tokenService  = mock(PartShareTokenCommandService.class);
        bandQueryService = mock(BandQueryService.class);
        var controller = new PartLinkRestController(partLinkQuery, shareByEmail, tokenService, bandQueryService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(
                        new org.springframework.http.converter.StringHttpMessageConverter(),
                        new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void getToken_mintsAndReturnsPublicUrlShape() throws Exception {
        UUID t = UUID.randomUUID();
        when(tokenService.tokenFor(anyLong(), anyString())).thenReturn(t);
        mvc.perform(get("/bands/1/compositions/1/parts/77/token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(t.toString()));
    }

    @Test
    void getToken_crossBand_conflict() throws Exception {
        doThrow(new IllegalStateException("Głos 77 innego zespołu"))
                .when(partLinkQuery).requireOpenable(anyLong(), anyLong(), anyLong());
        mvc.perform(get("/bands/1/compositions/1/parts/77/token"))
                .andExpect(status().isConflict());
        verify(tokenService, never()).tokenFor(anyLong(), any());
    }

    @Test
    void getToken_unknownBand_badRequest() throws Exception {
        // Layer 1 (band existence) is checked by BandQueryService BEFORE the token is minted.
        doThrow(new IllegalArgumentException("Band not found: 999"))
                .when(bandQueryService).getRequiredBand(999L);
        mvc.perform(get("/bands/999/compositions/1/parts/77/token"))
                .andExpect(status().isBadRequest());
        verify(tokenService, never()).tokenFor(anyLong(), any());
    }

    @Test
    void rotateToken_returnsFreshToken() throws Exception {
        UUID t = UUID.randomUUID();
        when(tokenService.rotate(anyLong(), anyString())).thenReturn(t);
        mvc.perform(post("/bands/1/compositions/1/parts/77/token/rotate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(t.toString()));
    }

    @Test
    void shareByEmail_recipientsSent() throws Exception {
        when(shareByEmail.share(anyLong(), anyLong(), anyLong(), any(), any())).thenReturn(2);
        var result = mvc.perform(post("/bands/1/compositions/1/parts/77/share")
                .contentType("application/json")
                .content("{\"recipientsCsv\":\"a@x.pl, b@y.pl\"}"))
                .andReturn();
        var resp = result.getResponse();
        org.junit.jupiter.api.Assertions.assertEquals(204, resp.getStatus(),
                "expected 204 no-content but got " + resp.getStatus());
    }

    @Test
    void shareByEmail_emptyRecipients_badRequest() throws Exception {
        when(shareByEmail.share(anyLong(), anyLong(), anyLong(), any(), any()))
                .thenThrow(new IllegalArgumentException("Podaj co najmniej jeden adres e-mail."));
        mvc.perform(post("/bands/1/compositions/1/parts/77/share")
                .contentType("application/json")
                .content("{\"recipientsCsv\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oldEnumerablePartUrl_noLongerStreamsTheWholePdf() throws Exception {
        // AC4: the numeric /parts/{id} GET method was deleted from the controller, so the
        // path is unmapped. Whatever the framework error handler answers (standalone MockMvc
        // turns an unmatched path into 404/405/500 depending on advice wiring), the contract
        // is: it is NEVER a 200 streaming the full score — the whole-PDF leak is structurally
        // gone, not merely hidden.
        mvc.perform(get("/bands/1/compositions/1/parts/77"))
                .andExpect(status().isGone());   // 410 tombstone — never a streamed PDF
    }

    @Test
    void shareFilename_isDeterministicAndPageScoped() {
        String f = PartLinkRestController.shareFilename("Polonez A-dur", "Flet 1", 23, 24);
        org.junit.jupiter.api.Assertions.assertEquals("polonez-a-dur-flet-1_ss-23-24.pdf", f);
    }
}
