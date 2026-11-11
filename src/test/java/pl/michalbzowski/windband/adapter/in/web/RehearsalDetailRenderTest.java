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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
                        .content(createBody)
                        .with(csrf()))
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
                        .content(createBody)
                        .with(csrf()))
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
                        )))
                        .with(csrf()))
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

    // === REGRESJA 2026-11: „Edytuj spotkanie → Zapisz zmiany” nie zapisywało w produkcji ===
    // (a) hx-put="/api/rehearsals/[[${rehearsal.id}]]" — inline Thymeleaf [[${...}]] NIE
    //     jest rozwijany w wartościach atrybutów (tylko w węzłach tekstowych), więc
    //     HTMX wysyłał „ducha” PUT-a na dosłowny szablon → 400 (brak konwersji Long);
    // (b) fetch() strony edycji BEZ nagłówka X-XSRF-TOKEN → CsrfFilter odrzucał PUT → 403.
    // Suity Selenium tego nie widziały: UiTestBase montuje hook CDP, który podkłada token
    // we wszystkich niesprawnych żądaniach (ten sam mechanizm jak w PR #328 / newFromPdf).

    @Test
    void editPage_rendersWorkingSubmitContract() throws Exception {
        org.springframework.mock.web.MockHttpSession session = loginAsAdmin();
        Long rehearsalId = createRehearsal(session, "Sala A");

        String html = mvc.perform(get("/rehearsals/" + rehearsalId + "/edit").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("formularz edycji jest na stronie")
                .contains("id=\"rehearsal-edit-form\"");
        assertThat(html).as("formularz jest wstępnie wypełniony istniejącym miejscem")
                .contains("Sala A");
        assertThat(html)
                .as("dosłowny szablon [[${...}]] nie może dotrzeć do przeglądarki — "
                        + "to on generował ghost-PUT na /api/rehearsals/[[${rehearsal.id}]] → 400")
                .doesNotContain("[[${rehearsal.id}]]");
        assertThat(html)
                .as("brak hx-put przy @RequestBody: endpoint chce JSON, a domyślne "
                        + "serializowanie formularza przez HTMX da 400 — ścieżką zapisu jest handler JS")
                .doesNotContain("hx-put=\"/api/rehearsals/");
        assertThat(html)
                .as("JS strony wysyła cookie XSRF-TOKEN jako nagłówek X-XSRF-TOKEN "
                        + "(bez tego CsrfFilter odrzuca PUT z 403)")
                .contains("csrfHeaders(");
    }

    @Test
    void updateRehearsal_withoutCsrfToken_isRejected403() throws Exception {
        org.springframework.mock.web.MockHttpSession session = loginAsAdmin();
        Long rehearsalId = createRehearsal(session, "Sala A");

        // Dokładna reprodukcja produkcyjna: PUT z fetch() bez X-XSRF-TOKEN.
        mvc.perform(put("/api/rehearsals/" + rehearsalId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Nowa sala")))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateRehearsal_withCsrfToken_updatesLocation() throws Exception {
        org.springframework.mock.web.MockHttpSession session = loginAsAdmin();
        Long rehearsalId = createRehearsal(session, "Stara sala");

        MvcResult result = mvc.perform(put("/api/rehearsals/" + rehearsalId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("DK Psary"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .as("nowe miejsce próby jest zapisane w encji (cel zgłoszenia: dodanie miejsca)")
                .contains("DK Psary");
    }

    @Test
    void detailPage_deleteHandlerSendsCsrf() throws Exception {
        org.springframework.mock.web.MockHttpSession session = loginAsAdmin();
        Long rehearsalId = createRehearsal(session, "Sala A");

        String html = mvc.perform(get("/rehearsals/" + rehearsalId).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("handler DELETE (modal usuwania) istnieje na stronie szczegółów")
                .contains("method: 'DELETE'");
        assertThat(html)
                .as("DELETE wysyła cookie XSRF-TOKEN jako nagłówek X-XSRF-TOKEN — bez tego "
                        + "CsrfFilter odrzuca usunięcie z 403 (ta sama klasa błędu co PUT w edycji)")
                .contains("X-XSRF-TOKEN");
    }

    @Test
    void deleteEndpoint_withoutCsrfToken_isRejected403() throws Exception {
        org.springframework.mock.web.MockHttpSession session = loginAsAdmin();
        Long rehearsalId = createRehearsal(session, "Sala A");

        mvc.perform(delete("/api/rehearsals/" + rehearsalId).session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteEndpoint_withCsrfToken_removesEntity() throws Exception {
        org.springframework.mock.web.MockHttpSession session = loginAsAdmin();
        Long rehearsalId = createRehearsal(session, "Sala A");

        mvc.perform(delete("/api/rehearsals/" + rehearsalId)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/rehearsals/" + rehearsalId).session(session))
                .andExpect(status().isNotFound());
    }

    private Long createRehearsal(org.springframework.mock.web.MockHttpSession session,
                                  String location) throws Exception {
        MvcResult created = mvc.perform(post("/api/rehearsals")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "date", "2026-12-31",
                                "startTime", "18:00",
                                "endTime", "20:00",
                                "location", location
                        )))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private String updateBody(String location) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "date", "2026-12-31",
                "startTime", "18:30",
                "endTime", "20:30",
                "location", location,
                "notes", "Uwagi testowe"
        ));
    }

    private org.springframework.mock.web.MockHttpSession loginAsAdmin() throws Exception {
        MvcResult loginResult = mvc.perform(post("/api/auth/login")
                        .param("username", "admin")
                        .param("password", "admin")
                        .with(csrf()))
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
