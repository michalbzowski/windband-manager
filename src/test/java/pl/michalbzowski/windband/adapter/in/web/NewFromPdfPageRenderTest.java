package pl.michalbzowski.windband.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Regression: the "new from PDF" form page must be RENDERED by Thymeleaf,
 * not emitted as a raw view-name body. A plain {@code @RestController}
 * serialization of the returned String once shipped exactly this bug to prod
 * (page showed the literal text "compositions/newFromPdf").
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class NewFromPdfPageRenderTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void newFromPdfForm_isRenderedTemplate_notRawViewName() throws Exception {
        MockHttpSession session = loginAsBandMember();

        MvcResult result = mvc.perform(get("/bands/1/compositions/new-from-pdf").session(session))
                .andExpect(status().isOk())
                .andReturn();
        String html = result.getResponse().getContentAsString();

        assertThat(html)
                .as("the Thymeleaf template is rendered — form present")
                .contains("id=\"nfp-start-form\"")
                .contains("Plik PDF z nutami");
        assertThat(html)
                .as("page must NOT be the raw view-name body (regression for prod bug)")
                .doesNotContain("compositions/newFromPdf");
    }

    // === Regression: CSRF na stan-changing POST-ach (flow „nowy utwór z PDF") ===
    // JS strony wysyłał wcześniej POST-y BEZ tokena; Spring Security je odrzucał, a w
    // przeglądarce trafiał HTML błędu zamiast JSON ("Unexpected token '<'").
    // UiTestBase montuje hook CDP podkładający X-XSRF-TOKEN w testach Selenium —
    // te testy MockMvc (bez hooka) zamykają tę lukę pokrycia.

    @Test
    void startPost_withoutCsrfToken_isRejected() throws Exception {
        MockHttpSession session = loginAsBandMember();

        mvc.perform(multipart("/bands/1/compositions/new-from-pdf/start")
                        .file(pdfFile())
                        .session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void startPost_withCsrfToken_succeeds_andReturnsIds() throws Exception {
        MockHttpSession session = loginAsBandMember();

        MvcResult result = mvc.perform(multipart("/bands/1/compositions/new-from-pdf/start")
                        .file(pdfFile())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertThat(json)
                .as("200 z id do polling, zgodnie z kontraktem UI")
                .contains("\"compositionId\":")
                .contains("\"analysisId\":");
    }

    @Test
    void servedPage_js_echoesXsrfTokenHeader_onStateChangingPosts() throws Exception {
        MockHttpSession session = loginAsBandMember();

        MvcResult result = mvc.perform(get("/bands/1/compositions/new-from-pdf").session(session))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .as("JS strony podbija cookie XSRF-TOKEN jako nagłówek X-XSRF-TOKEN")
                .contains("csrfHeaders()");
    }

    private MockMultipartFile pdfFile() {
        return new MockMultipartFile(
                "file", "Seven-magnificent.pdf", "application/pdf",
                "%PDF-1.4 test upload".getBytes(StandardCharsets.US_ASCII));
    }

    private MockHttpSession loginAsBandMember() throws Exception {
        MvcResult login = mvc.perform(post("/api/auth/login")
                        .param("username", "admin")
                        .param("password", "admin")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession();
    }
}
