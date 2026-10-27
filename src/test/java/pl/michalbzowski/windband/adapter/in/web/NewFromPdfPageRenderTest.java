package pl.michalbzowski.windband.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
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
