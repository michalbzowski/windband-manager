package pl.michalbzowski.windband.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renders the rehearsal DETAIL page and verifies the empty-after-create /
 * populated-after-invite contract. The rehearsal must mirror the event flow:
 * a freshly created rehearsal has NO attendance rows (no auto-invite), so
 * the detail page shows the empty-state message instead of a status table.
 * After a member is explicitly invited via {@code /api/rehearsals/{id}/invite},
 * the table renders exactly one row with the default {@code NO_RESPONSE} status.
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
    void freshRehearsalDetail_isEmpty() throws Exception {
        org.springframework.mock.web.MockHttpSession session = loginAsAdmin();

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

        String html = mvc.perform(get("/rehearsals/" + rehearsalId)
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Empty-state message is present
        assertThat(html).contains("Brak zaproszonych uczestników");
        // No status selects, no attendance rows on a fresh rehearsal
        assertThat(html).doesNotContain("id=\"status_");
        assertThat(countOccurrences(html, "<tr ")).isZero();
        // No PRESENT selected anywhere
        assertThat(countSelectedFor(html, "PRESENT"))
                .as("Fresh rehearsal must not mark any member as PRESENT")
                .isZero();
        // Toast container is still wired (UI is complete, just empty)
        assertThat(html).contains("id=\"toast-container\"");
        // The "Zapisz obecność" button is gone — attendance auto-saves on
        // .status-select change (delegated listener in windband-utils.js).
        assertThat(html).doesNotContain("id=\"save-attendance-btn\"");
        assertThat(html).doesNotContain("saveRehearsalAttendance");
        // The auto-save handler is loaded as a static JS bundle on every page.
        assertThat(html).contains("js/windband-utils.js");
    }

    @Test
    void detailAfterInvite_rendersRowForInvitedMemberOnly() throws Exception {
        org.springframework.mock.web.MockHttpSession session = loginAsAdmin();

        // Create rehearsal
        String createBody = objectMapper.writeValueAsString(java.util.Map.of(
                "date", "2026-07-21",
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
        Long rehearsalId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // Look up any active member (the test profile seeds members)
        MvcResult membersResult = mvc.perform(get("/api/members").session(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode members = objectMapper.readTree(membersResult.getResponse().getContentAsString());
        long memberId = -1L;
        for (JsonNode m : members) {
            if (m.path("active").asBoolean(true)) {
                memberId = m.path("id").asLong();
                break;
            }
        }
        assertThat(memberId)
                .as("test should have at least one active member to invite")
                .isPositive();

        // Invite that one member
        mvc.perform(post("/api/rehearsals/" + rehearsalId + "/invite")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "rehearsalId", rehearsalId,
                                "memberId", memberId
                        ))))
                .andExpect(status().is2xxSuccessful());

        // Render the detail page
        String html = mvc.perform(get("/rehearsals/" + rehearsalId)
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Empty-state message is gone
        assertThat(html).doesNotContain("Brak zaproszonych uczestników");
        // Exactly one status select for the invited member
        assertThat(countOccurrences(html, "id=\"status_")).isEqualTo(1);
        // The select's NO_RESPONSE option is marked selected, PRESENT is not
        assertThat(countSelectedFor(html, "NO_RESPONSE"))
                .as("Invited member must default to NO_RESPONSE")
                .isEqualTo(1);
        assertThat(countSelectedFor(html, "PRESENT"))
                .as("Invited member must NOT default to PRESENT")
                .isZero();
    }

    private org.springframework.mock.web.MockHttpSession loginAsAdmin() throws Exception {
        MvcResult loginResult = mvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (org.springframework.mock.web.MockHttpSession) loginResult.getRequest().getSession();
    }

    private int countSelectedFor(String html, String value) {
        int count = 0;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "<option[^>]*value=\"" + value + "\"[^>]*selected[^>]*>",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(html);
        while (m.find()) count++;
        return count;
    }

    private int countOccurrences(String html, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = html.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }
}
