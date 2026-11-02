package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #321 — kontrakt endpointu „same wiersze" ({@code GET /members/list/rows}).
 *
 * <p>Pad liter w wyszukiwarce brał się stąd, że żądania filtra zwracały CAŁY widok listy
 * (razem z inputem), a htmx podmieniał pasek razem z nim — odpowiedź niosła stare {@code q}.
 * Ten test spina kontrakt naprawy: odpowiedź /rows niesie WYŁĄCZNIE rejon listy oraz bloki
 * hx-swap-oob ze stanem paska (segment + licznik), a samego formularza filtrów nie zawiera.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MemberListRowsEndpointContractTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rowsEndpoint_returnsOnlyRegionAndOobState_neverTheFilterForm() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update(
                "INSERT INTO members (first_name, last_name, date_of_birth, active, joined_date, email_consent_given, band_id) " +
                "VALUES (?, ?, '1990-01-01', true, CURRENT_DATE, true, 1)",
                "Rowsonly" + uid, "Kontrakt" + uid);

        MvcResult loginResult = mvc.perform(post("/api/auth/login")
                        .param("username", "admin")
                        .param("password", "admin")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) loginResult.getRequest().getSession();

        MvcResult result = mvc.perform(get("/members/list/rows?q=Rowsonly" + uid).session(session))
                .andExpect(status().isOk())
                .andReturn();
        String html = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // 1. Odpowiedź jest rejonem listy — dokładnie jednym elementem korzeniowym.
        assertThat(html).contains("<div id=\"member-list-region\"");
        assertThat(html).contains("Kontrakt" + uid);

        // 2. Pasek filtrów NIE może być w odpowiedzi — podmiana inputu gubiła litery.
        assertThat(html).doesNotContain("<form id=\"member-filters\"");
        assertThat(html).doesNotContain("type=\"search\"");
        assertThat(html).doesNotContain("name=\"q\"");

        // 3. Stan paska jedzie jako bloki OOB — htmx podmieni tylko je, nie resztę widoku.
        assertThat(html).contains("hx-swap-oob=\"outerHTML\"");
        assertThat(html).contains("id=\"member-status-toggle\"");
        assertThat(html).contains("id=\"member-filters-status\"");

        // 4. Ukryte pole showInactive towarzyszy segmentowi — kolejne żądanie wyśle aktualny stan.
        assertThat(html).contains("name=\"showInactive\"");
    }

    @Test
    void fullFragment_stillCarriesTheWholeView_includingFilterForm() throws Exception {
        // Ścieżki pełnej podmiany (po zapisie członka, Ctrl+T na starej ścieżce) muszą
        // nadal działać bez zmian: #members-content to pasek + rejon.
        MvcResult loginResult = mvc.perform(post("/api/auth/login")
                        .param("username", "admin")
                        .param("password", "admin")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) loginResult.getRequest().getSession();

        MvcResult result = mvc.perform(get("/members/list").session(session))
                .andExpect(status().isOk())
                .andReturn();
        String html = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(html).contains("<form id=\"member-filters\"");
        assertThat(html).contains("id=\"member-list-region\"");
        // Bez partialRows kopie OOB nie mogą się pojawić (porządny DOM, bez duplikatów id).
        // Uwaga: sam token hx-swap-oob wolno występuje tylko w komentarzach — testujemy
        // postać atrybutu (z wartością), nie samo słowo.
        assertThat(html).doesNotContain("hx-swap-oob=\"");
    }
}
