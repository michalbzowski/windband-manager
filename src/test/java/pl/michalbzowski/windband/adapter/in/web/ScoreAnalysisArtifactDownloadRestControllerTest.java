package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.michalbzowski.windband.application.query.scoreanalysis.ScoreAnalysisArtifactQueryService;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-4.2 — HTTP-level contract of {@code GET /bands/{bandId}/compositions/{compositionId}/files?artifact=…}:
 * 200 streams the on-disk bytes with attachment disposition; service exceptions map through the
 * GlobalExceptionHandler (IAE → 400, ISE → 409). Mirrors {@link ScoreFileDownloadRestControllerTest}.
 */
@ExtendWith(MockitoExtension.class)
class ScoreAnalysisArtifactDownloadRestControllerTest {

    private MockMvc mvc;
    private ScoreAnalysisArtifactQueryService service;
    private Path jsonFile;

    @BeforeEach
    void setUp() throws Exception {
        service = Mockito.mock(ScoreAnalysisArtifactQueryService.class);
        var controller = new ScoreAnalysisArtifactDownloadRestController(service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        Path root = Path.of(System.getProperty("java.io.tmpdir"), "sa-artifact-ctrl-" + System.nanoTime());
        Files.createDirectories(root);
        jsonFile = root.resolve("arrangement.json");
        Files.writeString(jsonFile, "{\"bands\":[]}");
    }

    @Test
    @DisplayName("GET artefact of an owned analysis → 200 with attachment disposition and on-disk bytes")
    void downloadOwnArtefact_ok() throws Exception {
        when(service.open(anyLong(), anyLong(), Mockito.eq("json")))
                .thenReturn(new ScoreAnalysisArtifactQueryService.Artifact(
                        jsonFile, "arrangement.json", new MediaType("application", "json")));

        mvc.perform(get("/bands/1/compositions/100/files").param("artifact", "json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"arrangement.json\""))
                .andExpect(content().json("{\"bands\":[]}"));
    }

    @Test
    @DisplayName("Service throws IllegalArgumentException (unknown artefact / composition) → 400")
    void serviceIae_badRequest() throws Exception {
        doThrow(new IllegalArgumentException("Nieznany artefakt: wav"))
                .when(service).open(anyLong(), anyLong(), Mockito.eq("wav"));

        mvc.perform(get("/bands/1/compositions/100/files").param("artifact", "wav"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Service throws IllegalStateException (phase / missing file) → 409")
    void serviceIse_conflict() throws Exception {
        doThrow(new IllegalStateException("Artefakt \"json\" nie istnieje na dysku."))
                .when(service).open(anyLong(), anyLong(), Mockito.eq("json"));

        mvc.perform(get("/bands/1/compositions/100/files").param("artifact", "json"))
                .andExpect(status().isConflict());
    }
}
