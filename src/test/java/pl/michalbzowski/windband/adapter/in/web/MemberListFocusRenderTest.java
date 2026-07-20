package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Server-side proof that {@code GET /members/list?focus={id}} renders the list
 * fragment with {@code data-focus-id="{id}"} and each row carrying
 * {@code id="member-{id}"}. This isolates the cancel-edit scroll fix from the
 * Selenium layer (which only proves the button issues the request).
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MemberListFocusRenderTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void listFragmentWithFocusRendersFocusAttributes() throws Exception {
        MvcResult loginResult = mvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) loginResult.getRequest().getSession();

        MvcResult result = mvc.perform(get("/members/list?focus=2").session(session))
                .andExpect(status().isOk())
                .andReturn();
        String html = result.getResponse().getContentAsString();

        assertThat(html).contains("data-focus-id=\"2\"");
        assertThat(html).contains("id=\"member-2\"");
    }
}
