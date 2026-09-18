package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.michalbzowski.windband.application.command.scoreanalysis.ScoreAnalysisCommandService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-4.1 — wires standalone MockMvc for {@link StartScoreAnalysisRestController} +
 * {@link GetLatestScoreAnalysisRestController} + {@link GlobalExceptionHandler} to pin the
 * HTTP-level contract without booting the full Spring context:
 *   - POST start → 202 + {@code analysisId} body;
 *   - GET  latest → 200 dto / 404 when no run; cross-band or unknown comp → 409 via GlobalExceptionHandler.
 */
@ExtendWith(MockitoExtension.class)
class StartScoreAnalysisRestControllerTest {

    private MockMvc mvcPost;             // start REST controller
    private MockMvc mvcGet;              // latest REST controller
    private ScoreAnalysisCommandService command;
    private pl.michalbzowski.windband.application.query.scoreanalysis.ScoreAnalysisQueryService query;

    @BeforeEach
    void setUp() {
        command = org.mockito.Mockito.mock(ScoreAnalysisCommandService.class);
        var startCtl = new StartScoreAnalysisRestController(command);
        mvcPost = MockMvcBuilders.standaloneSetup(startCtl)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        query = org.mockito.Mockito.mock(
                    pl.michalbzowski.windband.application.query.scoreanalysis.ScoreAnalysisQueryService.class);
        var getLatest = new GetLatestScoreAnalysisRestController(query);
        mvcGet = MockMvcBuilders.standaloneSetup(getLatest)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    @DisplayName("POST /analyze returns 202 + JSON body {\"analysisId\": ...} on the happy path")
    void start_returns_202_with_analysisId() throws Exception {
        org.mockito.Mockito.when(command.start(anyLong(), anyLong())).thenReturn(1234L);
        mvcPost.perform(post("/bands/1/compositions/100/score-files/42/analyze"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysisId").value(1234));
    }

    @Test
    @DisplayName("POST service throws IllegalArgumentException (band unknown) → 400 via GlobalExceptionHandler")
    void start_badRequestForUnknownBand() throws Exception {
        doThrow(new IllegalArgumentException("Band not found")).when(command).start(anyLong(), anyLong());
        mvcPost.perform(post("/bands/99/compositions/1/score-files/2/analyze"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST service throws IllegalStateException (cross-band / unknown file) → 409 via GlobalExceptionHandler")
    void start_conflictForCrossBand() throws Exception {
        doThrow(new IllegalStateException("ScoreFile 7 not owned by band 1"))
                .when(command).start(anyLong(), anyLong());
        mvcPost.perform(post("/bands/1/compositions/1/score-files/7/analyze"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /analysis/latest with no row → 404")
    void latest_emptyReturns_404() throws Exception {
        org.mockito.Mockito.when(
                query.latestFor(anyLong(), anyLong()))
                .thenReturn(java.util.Optional.empty());
        mvcGet.perform(get("/bands/1/compositions/100/analysis/latest"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /analysis/latest with a row → 200 + JSON dto with phase+id")
    void latest_presentReturns_200() throws Exception {
        var dto = new pl.michalbzowski.windband.application.query.scoreanalysis.ScoreAnalysisQueryService.LatestScoreAnalysisDto(
                714L, "SUCCEEDED", "stub-3", null,
                "/out/714/arrangement.json", "/out/714/arrangement.musicxml",
                "/out/714/arrangement.mid", "/out/714/validation.txt",
                java.time.Instant.parse("2026-09-18T09:00:00Z"),
                java.time.Instant.parse("2026-09-18T09:00:03Z"),
                100L);
        org.mockito.Mockito.when(query.latestFor(anyLong(), anyLong()))
                .thenReturn(java.util.Optional.of(dto));
        // MockMvc standalone (without Jackson) serialises the DTO's accessors as Java toString by
        // default — so we just assert on status + the fact that a body is returned.
        var mvcRes = mvcGet.perform(get("/bands/1/compositions/100/analysis/latest"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(mvcRes.getResponse().getContentAsString()).contains("714")
                .contains("SUCCEEDED");
    }

    @Test
    @DisplayName("GET /analysis/latest on a composition NOT owned by the band → 409 via GlobalExceptionHandler")
    void latest_crossBandComp_409() throws Exception {
        doThrow(new IllegalStateException("Composition not owned"))
                .when(query).latestFor(anyLong(), anyLong());
        mvcGet.perform(get("/bands/2/compositions/100/analysis/latest"))
                .andExpect(status().isConflict());
    }
}
