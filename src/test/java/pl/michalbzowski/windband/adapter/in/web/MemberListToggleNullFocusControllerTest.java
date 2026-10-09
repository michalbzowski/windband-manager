package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #117 regression: clicking "Pokaż nieaktywnych" on the members list hits
 * {@code GET /members/list?showInactive=true&focus=null} — the JS toggle always
 * appended a literal {@code focus=} parameter, and with no member focused the URL
 * carried the string "null". Spring could not convert "null" to {@code Long} and the
 * fragment request failed (400), so the button visibly did nothing.
 *
 * <p>These tests pin the server contract: the literal "null" focus value must be
 * treated as "no focus" (200 + rendered list), including for the HTMX fragment
 * endpoint that the toggle actually calls.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MemberListToggleNullFocusControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void listFragment_withLiteralNullFocus_returnsOkAndShowsInactiveMembers() throws Exception {
        String uid = Long.toHexString(System.nanoTime());
        jdbcTemplate.update(
                "INSERT INTO members (first_name, last_name, date_of_birth, active, joined_date, email_consent_given, band_id) " +
                "VALUES (?, ?, '1988-01-02', false, CURRENT_DATE, false, 1)",
                "Wykluta" + uid, "Zosza" + uid);

        MvcResult loginResult = mvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) loginResult.getRequest().getSession();

        MvcResult result = mvc.perform(get("/members/list?showInactive=true&focus=null").session(session))
                .andExpect(status().isOk())
                .andReturn();
        String html = result.getResponse().getContentAsString();

        // The literal "null" param must not leak into the re-rendered fragment state.
        assertThat(html).doesNotContain("data-focus-id=\"null\"");
        // The toggle must actually surface inactive members once the request succeeds.
        assertThat(html).contains("Zosza" + uid);
    }

    @Test
    void listFragment_withNumericFocus_stillRendersThatMemberAsFocused() throws Exception {
        Long janId = jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE first_name = 'Jan' AND last_name = 'Kowalski'", Long.class);
        assertThat(janId).isNotNull();

        MvcResult loginResult = mvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) loginResult.getRequest().getSession();

        MvcResult result = mvc.perform(get("/members/list?focus=" + janId).session(session))
                .andExpect(status().isOk())
                .andReturn();
        String html = result.getResponse().getContentAsString();

        assertThat(html).contains("data-focus-id=\"" + janId + "\"");
    }
}
