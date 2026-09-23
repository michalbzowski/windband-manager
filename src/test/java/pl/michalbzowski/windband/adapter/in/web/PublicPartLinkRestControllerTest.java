package pl.michalbzowski.windband.adapter.in.web;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.michalbzowski.windband.application.query.composition.PartLinkQueryService;
import pl.michalbzowski.windband.application.query.composition.PublicPartLinkQueryService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-7.11 — HTTP contract of the public voice link: real sliced bytes, inline PDF headers,
 * no-store caching, and a uniform 404 for every failure mode (garbage segment, unknown token,
 * revoked token). The controller has no security config in standalone setup — matching the
 * {@code /public/**} permitAll rule asserted for real in {@code PublicPartLinkSecurityIT}.
 */
@ExtendWith(MockitoExtension.class)
class PublicPartLinkRestControllerTest {

    private MockMvc mvc;
    private PublicPartLinkQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = mock(PublicPartLinkQueryService.class);
        var controller = new PublicPartLinkRestController(queryService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(
                        new org.springframework.http.converter.ByteArrayHttpMessageConverter(),
                        new org.springframework.http.converter.ResourceHttpMessageConverter(),
                        new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void validToken_streamsInlinePdfWithSlicedFilename() throws Exception {
        // %PDF- magic so the body "looks like" a real slice.
        byte[] slice = new byte[] { '%', 'P', 'D', 'F', '-', '1', '.', '7' };
        when(queryService.openByToken(any(UUID.class))).thenReturn(
                new PublicPartLinkQueryService.PublicPart("Polonez A-dur", "Flet 1", 2, 3, "application/pdf", slice));

        mvc.perform(get("/public/parts/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "inline; filename=\"polonez-a-dur-flet-1_ss-2-3.pdf\""))
                .andExpect(header().string("Cache-Control", "private, no-store"));
    }

    @Test
    void unknownToken_uniform404() throws Exception {
        when(queryService.openByToken(any(UUID.class)))
                .thenThrow(new PublicPartLinkQueryService.TokenNotFoundException());
        mvc.perform(get("/public/parts/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void garbagePathSegment_isUniform404_not500() throws Exception {
        // A non-UUID segment must not reach UUID parsing as a server error.
        mvc.perform(get("/public/parts/not-even-a-uuid"))
                .andExpect(status().isNotFound());
    }

    @Test
    void noCoveringFile_conflict() throws Exception {
        when(queryService.openByToken(any(UUID.class)))
                .thenThrow(new PartLinkQueryService.NoCoveringFileException("brak pliku"));
        mvc.perform(get("/public/parts/" + UUID.randomUUID()))
                .andExpect(status().isConflict());
    }
}
