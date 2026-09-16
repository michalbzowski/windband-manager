package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import pl.michalbzowski.windband.application.query.composition.ScoreFileDownloadQueryService;
import pl.michalbzowski.windband.application.query.composition.ScoreFileDownloadMetadata;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-2.4 — wires the standalone MockMvc for {@link ScoreFileDownloadRestController} + {@link GlobalExceptionHandler}
 * so we can assert the HTTP-level contract without starting a full Spring context: Content-Type,
 * Content-Disposition (inline vs. attachment), and status codes for the 400/409/410 paths.
 */
@ExtendWith(MockitoExtension.class)
class ScoreFileDownloadRestControllerTest {

    private MockMvc mvc;
    private ScoreFileDownloadQueryService service;

    @BeforeEach
    void setUp() {
        service = mock(ScoreFileDownloadQueryService.class);
        var controller = new ScoreFileDownloadRestController(service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ScoreFileDownloadQueryService.Download handle(byte[] bytes, String name, String mime, boolean zip) {
        var meta = new ScoreFileDownloadMetadata(42L, name, mime, (long) bytes.length, zip);
        return new ScoreFileDownloadQueryService.Download(
                new ByteArrayInputStream(bytes), meta);
    }

    @Test
    @DisplayName("PDF download → 200 + Content-Disposition inline;filename=\"score.pdf\" + body bytes")
    void downloadPdf_inline() throws Exception {
        byte[] body = "PDF-BYTES".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(service.open(anyLong(), anyLong(), anyLong())).thenAnswer(inv -> handle(body, "score.pdf", "application/pdf", false));

        mvc.perform(get("/bands/1/compositions/100/files/42"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"score.pdf\""))
                .andExpect(content().bytes(body));
    }

    @Test
    @DisplayName("ZIP download → 200 + Content-Disposition attachment;filename=\"parts.zip\"")
    void downloadZip_attachment() throws Exception {
        when(service.open(anyLong(), anyLong(), anyLong()))
                .thenAnswer(inv -> handle(new byte[] { 0, 1 }, "parts.zip", "application/zip", true));

        mvc.perform(get("/bands/1/compositions/100/files/42"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"parts.zip\""));
    }

    @Test
    @DisplayName("Service throws IllegalArgumentException (missing band) → 400 from the global handler")
    void downloadUnknownBand_badRequest() throws Exception {
        when(service.open(anyLong(), anyLong(), anyLong()))
                .thenThrow(new IllegalArgumentException("Band not found: 99"));

        mvc.perform(get("/bands/99/compositions/100/files/42"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Service throws IllegalStateException (cross-band) → 409 from the global handler")
    void downloadCrossBand_conflict() throws Exception {
        when(service.open(anyLong(), anyLong(), anyLong()))
                .thenThrow(new IllegalStateException("ScoreFile 7 does not belong to band 1"));

        mvc.perform(get("/bands/1/compositions/100/files/7")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Service throws ScoreFileMissingException (path missing) → 410 from the global handler")
    void downloadMissingOnDisk_gone() throws Exception {
        when(service.open(anyLong(), anyLong(), anyLong()))
                .thenThrow(new ScoreFileDownloadQueryService.ScoreFileMissingException(
                        "Nie udało się odczytać pliku 45 z dysku."));

        mvc.perform(get("/bands/1/compositions/100/files/45")).andExpect(status().isGone());
    }
}
