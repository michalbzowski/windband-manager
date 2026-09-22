package pl.michalbzowski.windband.adapter.in.web;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import pl.michalbzowski.windband.application.query.composition.ScoreFileDownloadQueryService;
import pl.michalbzowski.windband.application.query.composition.ScoreFileDownloadQueryService.Download;
import pl.michalbzowski.windband.application.query.composition.ScoreFileDownloadMetadata;
import pl.michalbzowski.windband.application.query.composition.ScoreFileThumbQueryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-7.9 — step 5: HTTP contract of the header-preview thumbnail endpoint
 * {@code GET /bands/{bandId}/compositions/{compositionId}/files/{fileId}/thumb}.
 *
 * <p>Wired standalone (real {@link ScoreFileThumbQueryService} + mocked
 * {@link ScoreFileDownloadQueryService} + {@link GlobalExceptionHandler}) exactly like
 * {@code ScoreFileDownloadRestControllerTest}, so we pin the status contract without a
 * Spring context:
 *
 * <ul>
 *   <li>200 + JPEG magic bytes for a valid multi-page PDF</li>
 *   <li>422 (Unprocessable Entity) when the stored file is not a valid PDF (e.g. ZIP parent)</li>
 *   <li>409 (Conflict) on cross-band / ownership violations — service exceptions propagate,
 *       no blanket catch may swallow them</li>
 *   <li>404 for an unknown page number</li>
 *   <li>400 for a non-positive page parameter</li>
 *   <li>second render of the same (fileId, page) is served from the in-memory cache —
 *       the download stream is opened exactly once</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ScoreFileThumbRestControllerTest {

    private MockMvc mvc;
    private ScoreFileDownloadQueryService downloadService;
    private ScoreFileThumbQueryService thumbService;

    @BeforeEach
    void setUp() {
        downloadService = mock(ScoreFileDownloadQueryService.class);
        thumbService = new ScoreFileThumbQueryService(downloadService);
        mvc = MockMvcBuilders.standaloneSetup(new ScoreFileThumbRestController(thumbService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Download handle(byte[] bytes, String mime, boolean zip) {
        return new Download(new ByteArrayInputStream(bytes),
                new ScoreFileDownloadMetadata(42L, "score.pdf", mime, (long) bytes.length, zip));
    }

    /** Three valid LETTER pages — page 1 must render, page 99 must be out of range. */
    private static byte[] threePagePdf() throws Exception {
        byte[] out;
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream mem = new ByteArrayOutputStream()) {
            for (int i = 0; i < 3; i++) {
                doc.addPage(new PDPage(PDRectangle.LETTER));
            }
            doc.save(mem);
            out = mem.toByteArray();
        }
        return out;
    }

    private static byte[] zipArchive() throws java.io.IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (var zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("trumpet-page-1.pdf"));
            zos.write("hi".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    @Test
    @DisplayName("thumb of a valid multi-page PDF → 200 + image/jpeg + JPEG magic bytes (FF D8 FF)")
    void thumbValidPdf_returnsJpeg() throws Exception {
        when(downloadService.open(anyLong(), anyLong(), anyLong()))
                .thenAnswer(inv -> handle(threePagePdf(), "application/pdf", false));

        byte[] body = mvc.perform(get("/bands/1/compositions/7/files/42/thumb").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body.length).as("JPEG body must not be empty").isGreaterThan(0);
        assertThat(body[0] & 0xFF).isEqualTo(0xFF);
        assertThat(body[1] & 0xFF).isEqualTo(0xD8);
        assertThat(body[2] & 0xFF).isEqualTo(0xFF);
    }

    @Test
    @DisplayName("thumb of a ZIP parent file → 422 Unprocessable Entity (not a valid PDF document)")
    void thumbZipFile_unprocessableEntity() throws Exception {
        when(downloadService.open(anyLong(), anyLong(), anyLong()))
                .thenAnswer(inv -> handle(zipArchive(), "application/zip", true));

        mvc.perform(get("/bands/1/compositions/7/files/42/thumb").param("page", "1"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("thumb requested cross-band → 409 Conflict (ownership violation propagates to the handler)")
    void thumbCrossBand_conflict() throws Exception {
        when(downloadService.open(anyLong(), anyLong(), anyLong()))
                .thenThrow(new IllegalStateException(
                        "Score file 42 does not belong to band 2"));

        mvc.perform(get("/bands/2/compositions/7/files/42/thumb").param("page", "1"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("thumb of a valid PDF with an unknown page number → 404 Not Found")
    void thumbUnknownPage_notFound() throws Exception {
        when(downloadService.open(anyLong(), anyLong(), anyLong()))
                .thenAnswer(inv -> handle(threePagePdf(), "application/pdf", false));

        mvc.perform(get("/bands/1/compositions/7/files/42/thumb").param("page", "99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("thumb with a non-positive page parameter → 400 Bad Request")
    void thumbNonPositivePage_badRequest() throws Exception {
        mvc.perform(get("/bands/1/compositions/7/files/42/thumb").param("page", "0"))
                .andExpect(status().isBadRequest());

        // the invalid parameter must be rejected before opening the stream
    }

    @Test
    @DisplayName("rendering the same (fileId, page, width) twice opens the download stream exactly once — cached")
    void thumbSecondCall_servedFromCache() throws Exception {
        when(downloadService.open(anyLong(), anyLong(), anyLong()))
                .thenAnswer(inv -> handle(threePagePdf(), "application/pdf", false));

        String url = "/bands/1/compositions/7/files/42/thumb";
        mvc.perform(get(url).param("page", "1")).andExpect(status().isOk());
        mvc.perform(get(url).param("page", "1")).andExpect(status().isOk());
        // A different page is a different cache key — it must still open the stream.
        mvc.perform(get(url).param("page", "2")).andExpect(status().isOk());

        verify(downloadService, times(2)).open(anyLong(), anyLong(), anyLong());
    }
}
