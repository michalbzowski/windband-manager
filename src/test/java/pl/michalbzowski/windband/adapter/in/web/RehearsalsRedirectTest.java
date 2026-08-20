package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for /rehearsals route redirect logic.
 * All requests to deprecated /rehearsals routes should redirect to /meetings.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RehearsalsRedirectTest {

    @Autowired
    private MockMvc mvc;

    private org.springframework.mock.web.MockHttpSession login() throws Exception {
        MvcResult login = mvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession();
    }

    @Test
    void rehearsalsList_redirectsToMeetings() throws Exception {
        org.springframework.mock.web.MockHttpSession session = login();

        mvc.perform(get("/rehearsals")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/meetings"));
    }

    @Test
    void rehearsalsListFragment_redirectsToMeetings() throws Exception {
        org.springframework.mock.web.MockHttpSession session = login();

        mvc.perform(get("/rehearsals/list")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/meetings"));
    }

    @Test
    void rehearsalsNew_redirectsToMeetingsNew() throws Exception {
        org.springframework.mock.web.MockHttpSession session = login();

        mvc.perform(get("/rehearsals/new")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/meetings/new"));
    }

    @Test
    void rehearsalDetail_redirectsToMeetings() throws Exception {
        org.springframework.mock.web.MockHttpSession session = login();

        mvc.perform(get("/rehearsals/123")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/meetings"));
    }

    @Test
    void rehearsalEdit_redirectsToMeetingsNew() throws Exception {
        org.springframework.mock.web.MockHttpSession session = login();

        mvc.perform(get("/rehearsals/123/edit")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/meetings/new"));
    }
}
