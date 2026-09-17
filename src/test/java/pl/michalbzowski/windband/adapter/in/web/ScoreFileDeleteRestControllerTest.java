package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.michalbzowski.windband.application.command.composition.ScoreFileDeleteCommandService;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-2.5 — wires a standalone MockMvc for {@link ScoreFileDeleteRestController} +
 * {@link GlobalExceptionHandler} to pin the HTTP-level contract of
 * {@code DELETE /bands/{bandId}/compositions/{compositionId}/files/{fileId}} without
 * booting a full Spring context: 204 on success, 400 for an unknown band, 409 for the
 * cross-band / unknown-file paths. Mirrors {@link ScoreFileDownloadRestControllerTest}.
 */
@ExtendWith(MockitoExtension.class)
class ScoreFileDeleteRestControllerTest {

    private MockMvc mvc;
    private ScoreFileDeleteCommandService service;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(ScoreFileDeleteCommandService.class);
        var controller = new ScoreFileDeleteRestController(service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("DELETE on an owned score file → 204 No Content")
    void deleteOwnFile_noContent() throws Exception {
        mvc.perform(delete("/bands/1/compositions/100/files/42"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Service throws IllegalArgumentException (unknown band) → 400 from the global handler")
    void deleteUnknownBand_badRequest() throws Exception {
        doThrow(new IllegalArgumentException("Band not found: 99"))
                .when(service).delete(anyLong(), anyLong(), anyLong());

        mvc.perform(delete("/bands/99/compositions/100/files/42"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Service throws IllegalStateException (cross-band) → 409 from the global handler")
    void deleteCrossBand_conflict() throws Exception {
        doThrow(new IllegalStateException("Score file 7 does not belong to band 1"))
                .when(service).delete(anyLong(), anyLong(), anyLong());

        mvc.perform(delete("/bands/1/compositions/100/files/7"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Service throws IllegalStateException (unknown file) → 409 from the global handler")
    void deleteUnknownFile_conflict() throws Exception {
        doThrow(new IllegalStateException("Score file 404 does not exist."))
                .when(service).delete(anyLong(), anyLong(), anyLong());

        mvc.perform(delete("/bands/1/compositions/100/files/404"))
                .andExpect(status().isConflict());
    }
}
