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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kontrakt CSRF formularzy wydarzeń i sprawozdania (rejestracja 2026-11-11).
 *
 * Produkcja: POST /api/events z formularza „Dodaj wydarzenie" (events/form.html)
 * był wysyłany przez raw fetch() BEZ nagłówka X-XSRF-TOKEN → CsrfFilter odrzucał
 * request z 403; Spring Boot odpowiadał ciałem JSON (błąd 403), a handler JS traktował
 * to jako sukces (response.json() się udał) i przekierowywał do /events — bez żadnego
 * komunikatu o błędzie dla użytkownika. To ta sama klasa błędu co edycja/rehearsals
 * (fix 65efec3b) i new-from-pdf (fix d9e5c836, PR #328) — te same suity Selenium tego
 * nie widziały, bo UiTestBase montuje hook CDP podkładający token we wszystkich żądaniach.
 *
 * Naprawa: strony wysyłają cookie XSRF-TOKEN jako nagłówek X-XSRF-TOKEN
 * (helper csrfTokenHeaders()) oraz tworzenie wydarzenia zatrzymuje nawigację przy
 * odpowiedzi innej niż 2xx i pokazuje toast z powodem.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class EventFormCsrfContractTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    // === REGRESJA 2026-11: „Dodaj wydarzenie" cicho nie tworzyło (403 CSRF + ghost-navigation) ===
    // form.html: fetch('/api/events') bez nagłówka X-XSRF-TOKEN; przy 403 .json() się udawał
    // (body JSON z /error) i window.location.href='/events' w gałęzi sukcesu — zero komunikatu.

    @Test
    void createFormSendsCsrfTokenHeader() throws Exception {
        org.springframework.mock.web.MockHttpSession session = loginAsAdmin();

        String html = mvc.perform(get("/events/new?paymentType=FREE").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("handler utworzenia wydaje cookie XSRF-TOKEN jako nagłówek X-XSRF-TOKEN "
                        + "(helper csrfTokenHeaders()) — bez tego CsrfFilter odrzuca POST z 403 "
                        + "i użytkownik nie widzi żadnego komunikatu, bo 403 wygląda jak sukces)")
                .contains("csrfTokenHeaders()");
    }

    @Test
    void createEvent_withoutCsrfToken_isRejected403() throws Exception {
        org.springframework.mock.web.MockHttpSession session = loginAsAdmin();

        // Dokładna reprodukcja produkcyjna: POST z fetch() bez X-XSRF-TOKEN.
        mvc.perform(post("/api/events")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Koncert bezpłatny", "FREE")))
                .andExpect(status().isForbidden());

        // Wydarzenie nie powstało — lista wydarzeń (strona Thymeleaf, ścieżka produkcyjna)
        // nie zawiera jego nazwy. Uwaga: JSON GET /api/events to osobny latentny 500
        // (LazyInitializationException przy serializacji) — poza zakresem tej naprawy.
        String listHtml = mvc.perform(get("/events").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(listHtml).as("odrzucone (403) tworzenie nie mogło zapisać encji")
                .doesNotContain("Koncert bezpłatny");
    }

    @Test
    void createEvent_withCsrfToken_returns201WithId() throws Exception {
        org.springframework.mock.web.MockHttpSession session = loginAsAdmin();

        MvcResult created = mvc.perform(post("/api/events")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Koncert charytatywny", "FREE"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        assertThat(body.get("id").asLong()).isPositive();
        assertThat(body.get("name").asText()).isEqualTo("Koncert charytatywny");
    }

    // === Edycja wydarzenia (events/edit.html) — ta sama klasa: PUT bez tokenu ===

    @Test
    void editFormSendsCsrfTokenHeader() throws Exception {
        org.springframework.mock.web.MockHttpSession session = loginAsAdmin();
        Long eventId = createEventViaApi(session, "Koncert do edycji");

        String html = mvc.perform(get("/events/" + eventId + "/edit").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("handler zapisu wydaje cookie XSRF-TOKEN jako nagłówek X-XSRF-TOKEN "
                        + "(helper csrfTokenHeaders()) — bez tego CsrfFilter odrzuca PUT z 403")
                .contains("csrfTokenHeaders()");
    }

    @Test
    void updateEvent_withoutCsrfToken_isRejected403() throws Exception {
        org.springframework.mock.web.MockHttpSession session = loginAsAdmin();
        Long eventId = createEventViaApi(session, "Koncert do edycji");

        mvc.perform(put("/api/events/" + eventId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(eventId, "Nowy tytuł")))
                .andExpect(status().isForbidden());

        // Stan bez zmian — 403 nie zmodyfikował encji (weryfikacja przez widok szczegółów).
        assertThat(fetchDetailHtml(session, eventId)).contains("Koncert do edycji");
    }

    @Test
    void updateEvent_withCsrfToken_updatesDetails() throws Exception {
        org.springframework.mock.web.MockHttpSession session = loginAsAdmin();
        Long eventId = createEventViaApi(session, "Stary tytuł");

        mvc.perform(put("/api/events/" + eventId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(eventId, "Nowy tytuł"))
                        .with(csrf()))
                .andExpect(status().is2xxSuccessful());

        assertThat(fetchDetailHtml(session, eventId))
                .as("nowa nazwa wydarzenia jest zapisana (cel zgłoszenia: edycja ma działać)")
                .contains("Nowy tytuł");
    }

    // === Sprawozdanie (reports/sprawozdanie-customize.html) — POST bez tokenu ===

    @Test
    void reportCustomizeFormSendsCsrfTokenHeader() throws Exception {
        org.springframework.mock.web.MockHttpSession session = loginAsAdmin();

        String html = mvc.perform(get("/reports/sprawozdanie/customize").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("generator sprawozdania wydaje cookie XSRF-TOKEN jako nagłówek X-XSRF-TOKEN "
                        + "(helper csrfTokenHeaders()) — bez tego CsrfFilter odrzuca POST "
                        + "/api/reports/... z 403 (w UI widoczne tylko jako ogólny komunikat o błędzie serwera)")
                .contains("csrfTokenHeaders()");
    }

    // === Pomocnicze ===

    private Long createEventViaApi(org.springframework.mock.web.MockHttpSession session,
                                   String name) throws Exception {
        MvcResult created = mvc.perform(post("/api/events")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(name, "FREE"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private String fetchDetailHtml(org.springframework.mock.web.MockHttpSession session, Long eventId)
            throws Exception {
        // Ścieżka produkcyjna (widok szczegółów); JSON-owy GET /api/events/{id} to
        // osobny latentny 500 (LazyInitializationException) — poza zakresem tej naprawy.
        return mvc.perform(get("/events/" + eventId).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String createBody(String name, String paymentType) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "name", name,
                "date", "2027-03-15",
                "startTime", "18:00",
                "location", "DK Młodzieżowa",
                "eventType", "CONCERT",
                "paymentType", paymentType
        ));
    }

    private String updateBody(Long eventId, String name) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "id", eventId,
                "name", name,
                "date", "2027-03-15",
                "startTime", "18:30",
                "location", "DK Młodzieżowa",
                "eventType", "CONCERT",
                "paymentType", "FREE"
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
}
