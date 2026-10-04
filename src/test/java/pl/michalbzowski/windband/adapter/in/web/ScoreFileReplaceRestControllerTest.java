package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.michalbzowski.windband.application.command.composition.ScoreFileCommandService;
import pl.michalbzowski.windband.application.command.composition.ScoreFileReplaceCommandService;
import pl.michalbzowski.windband.application.command.composition.ScoreFileUploadRequest;
import pl.michalbzowski.windband.application.command.composition.UploadValidator.UploadRejectedException;
import pl.michalbzowski.windband.application.dto.composition.ScoreFileDto;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-7.15 — HTTP contract of {@code POST /bands/{bandId}/compositions/{compositionId}/files/{fileId}/replace}
 * on a standalone MockMvc (no Spring context): the multipart part must reach the replace command
 * service with the path ids, a success returns the UPDATED row (200 + ScoreFileDto JSON), and the
 * rejection statuses ride the same global-handler mapping as the upload endpoint
 * (UploadRejectedException → its own code, IllegalStateException → 409, IllegalArgumentException → 400).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScoreFileReplaceRestControllerTest {

    @Mock private ScoreFileCommandService commandService;
    @Mock private ScoreFileReplaceCommandService replaceService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var controller = new ScoreFileUploadRestController(
                commandService, replaceService, new UploadedFileAssembler());
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private MockMultipartFile pdfPart() {
        return new MockMultipartFile("file", "marsz_v2.pdf", "application/pdf", new byte[]{1, 2, 3});
    }

    @Test
    @DisplayName("POST replace with a PDF part → 200 + the updated row DTO; service sees the path ids")
    void replaceOwnedFile_ok() throws Exception {
        when(replaceService.replace(any(ScoreFileUploadRequest.class), eq(20L), eq(100L), eq(1L)))
                .thenReturn(new ScoreFileDto(20L, 100L, "marsz_v2.pdf", 3L, "application/pdf", 3,
                        false, "bb".repeat(32), null, Instant.now()));

        mvc.perform(multipart("/bands/1/compositions/100/files/20/replace").file(pdfPart()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(20))
                .andExpect(jsonPath("$.originalName").value("marsz_v2.pdf"))
                .andExpect(jsonPath("$.pageCount").value(3));
    }

    @Test
    @DisplayName("Service throws UploadRejectedException(422) (ZIP parent / MIME mismatch / range shrink) → 422 + message")
    void replaceRejected_mapsOwnStatus() throws Exception {
        when(replaceService.replace(any(), eq(20L), eq(100L), eq(1L)))
                .thenThrow(new UploadRejectedException(422, "Nowy plik ma 2 strony, a głosy są zmapowane poza ten zakres."));

        mvc.perform(multipart("/bands/1/compositions/100/files/20/replace").file(pdfPart()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("poza ten zakres")));
    }

    @Test
    @DisplayName("Service throws IllegalStateException (cross-band / unknown file) → 409")
    void replaceCrossBand_conflict() throws Exception {
        when(replaceService.replace(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("Score file 20 does not belong to band 1"));

        mvc.perform(multipart("/bands/1/compositions/100/files/20/replace").file(pdfPart()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Service throws IllegalArgumentException (unknown band) → 400")
    void replaceUnknownBand_badRequest() throws Exception {
        when(replaceService.replace(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Band not found: 99"));

        mvc.perform(multipart("/bands/99/compositions/100/files/20/replace").file(pdfPart()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Missing 'file' part → rejected before the service runs (advice's generic handler wins over DefaultHandlerExceptionResolver, mirroring the upload endpoint)")
    void replaceWithoutFilePart_isRejected() throws Exception {
        mvc.perform(multipart("/bands/1/compositions/100/files/20/replace")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().is5xxServerError());
    }
}
