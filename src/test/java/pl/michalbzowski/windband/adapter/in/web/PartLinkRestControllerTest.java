package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import pl.michalbzowski.windband.application.command.composition.PartShareByEmailCommandService;
import pl.michalbzowski.windband.application.query.composition.PartLinkQueryService;
import pl.michalbzowski.windband.application.query.composition.ScoreFileDownloadQueryService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PartLinkRestControllerTest {

    private MockMvc mvc;
    private PartLinkQueryService partLinkQuery;
    private ScoreFileDownloadQueryService downloadQuery;
    private PartShareByEmailCommandService shareByEmail;

    @BeforeEach
    void setUp() {
        partLinkQuery = mock(PartLinkQueryService.class);
        downloadQuery = mock(ScoreFileDownloadQueryService.class);
        shareByEmail  = mock(PartShareByEmailCommandService.class);
        var controller = new PartLinkRestController(partLinkQuery, downloadQuery, shareByEmail, null);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(
                        new org.springframework.http.converter.ByteArrayHttpMessageConverter(),
                        new org.springframework.http.converter.StringHttpMessageConverter(),
                        new org.springframework.http.converter.ResourceHttpMessageConverter(),
                        new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static PartLinkQueryService.PartLink mk(long partId) {
        return new PartLinkQueryService.PartLink(
                partId, 1L, 1L, 99L, "score.pdf", "application/pdf", 8000L, "Utwór", "Flet", 23, 24);
    }

    @Test
    void getPartLink_pdfInline() throws Exception {
        byte[] body = new byte[] { (byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46 };
        var handle = new ScoreFileDownloadQueryService.Download(
                new ByteArrayInputStream(body),
                new pl.michalbzowski.windband.application.query.composition.ScoreFileDownloadMetadata(
                        99L, "score.pdf", "application/pdf", 4L, false));
        when(partLinkQuery.open(anyLong(), anyLong(), anyLong())).thenReturn(mk(77));
        when(downloadQuery.open(anyLong(), anyLong(), anyLong())).thenReturn(handle);
        mvc.perform(get("/bands/1/compositions/1/parts/77"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"utwor-flet_ss-23-24.pdf\""));
    }

    @Test
    void getPartLink_unknownBand_badRequest() throws Exception {
        when(partLinkQuery.open(anyLong(), anyLong(), anyLong()))
                .thenThrow(new IllegalArgumentException("Band not found: 999"));
        mvc.perform(get("/bands/999/compositions/1/parts/77"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPartLink_crossBand_conflict() throws Exception {
        when(partLinkQuery.open(anyLong(), anyLong(), anyLong()))
                .thenThrow(new IllegalStateException("Głos 77 innego zespołu"));
        mvc.perform(get("/bands/1/compositions/1/parts/77"))
                .andExpect(status().isConflict());
    }

    @Test
    void shareByEmail_recipientsSent() throws Exception {
        when(shareByEmail.share(anyLong(), anyLong(), anyLong(), any(), any())).thenReturn(2);
        var result = mvc.perform(post("/bands/1/compositions/1/parts/77/share")
                .contentType("application/json")
                .content("{\"recipientsCsv\":\"a@x.pl, b@y.pl\"}"))
                .andReturn();
        var resp = result.getResponse();
        org.junit.jupiter.api.Assertions.assertEquals(204, resp.getStatus(), "expected 204 no-content but got " + resp.getStatus());
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
}
