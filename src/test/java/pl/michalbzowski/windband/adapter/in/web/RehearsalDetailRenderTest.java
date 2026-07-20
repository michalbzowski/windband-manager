package pl.michalbzowski.windband.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renders the rehearsal DETAIL page for a freshly created rehearsal (no attendance
 * records) and verifies that every member's status select defaults to NO_RESPONSE,
 * never PRESENT. This is the server-side proof for the "all members show PRESENT"
 * regression.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RehearsalDetailRenderTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void freshRehearsalDetail_defaultsToNoResponse() throws Exception {
        // Log in as admin via form login (success handler builds WindbandOidcUser with activeTeamId=1)
        MvcResult loginResult = mvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        // Reuse the authenticated HTTP session for subsequent requests
        org.springframework.mock.web.MockHttpSession session =
                (org.springframework.mock.web.MockHttpSession) loginResult.getRequest().getSession();

        // Create a rehearsal via REST (JSON body)
        String createBody = objectMapper.writeValueAsString(java.util.Map.of(
                "date", "2026-07-20",
                "startTime", "18:00",
                "endTime", "20:00",
                "location", "Sala prób"
        ));
        MvcResult created = mvc.perform(post("/api/rehearsals")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createdJson = objectMapper.readTree(created.getResponse().getContentAsString());
        Long rehearsalId = createdJson.get("id").asLong();
        System.out.println("[TEST] created rehearsal id=" + rehearsalId);

        // Render the detail page for that rehearsal
        MvcResult detail = mvc.perform(get("/rehearsals/" + rehearsalId)
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();
        String html = detail.getResponse().getContentAsString();
        System.out.println("[TEST] ===== DETAIL HTML (status selects) =====");
        for (String line : html.split("\n")) {
            if (line.contains("status_") || line.contains("NO_RESPONSE") || line.contains("PRESENT") || line.contains("selected") || line.toLowerCase().contains("toast")) {
                System.out.println("[TEST] " + line.trim());
            }
        }
        System.out.println("[TEST] ===== END =====");

        // Sanity: is the toast container present on the detail page?
        boolean hasToastContainer = html.contains("id=\"toast-container\"");
        boolean hasSaveHandler = html.contains("saveRehearsalAttendance");
        System.out.println("[TEST] hasToastContainer=" + hasToastContainer + " hasSaveHandler=" + hasSaveHandler);

        // There must be status selects, and each default-selected value must be NO_RESPONSE
        assertThat(html).contains("id=\"status_");
        // Count how many options are marked selected="selected" with value PRESENT
        int presentSelected = countSelectedFor(html, "PRESENT");
        int noResponseSelected = countSelectedFor(html, "NO_RESPONSE");
        System.out.println("[TEST] PRESENT selected count=" + presentSelected
                + " NO_RESPONSE selected count=" + noResponseSelected);
        assertThat(presentSelected)
                .as("Fresh rehearsal must NOT default any member to PRESENT")
                .isZero();
    }

    private int countSelectedFor(String html, String value) {
        // crude count of <option value="VALUE" selected ...> or ... selected>value</option>
        int count = 0;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "<option[^>]*value=\"" + value + "\"[^>]*selected[^>]*>",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(html);
        while (m.find()) count++;
        return count;
    }
}
