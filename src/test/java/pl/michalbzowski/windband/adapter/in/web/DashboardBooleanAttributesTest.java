package pl.michalbzowski.windband.adapter.in.web;

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
 * Verifies that the dashboard "Członkowie" section shows one row per BOOLEAN
 * member attribute, with the count of members whose value is "true".
 *
 * <p>Real flow: create a BOOLEAN attribute via the API, set it to "true" for a
 * member, then render GET / and assert the attribute name appears in the
 * dashboard output. Uses @SpringBootTest (like RehearsalDetailRenderTest) so
 * form-login auth works. The counting logic itself is covered by
 * MemberAttributeQueryServiceTest.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DashboardBooleanAttributesTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void dashboardShowsBooleanAttributeCounts() throws Exception {
        MvcResult login = mvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession();

        // 1) create BOOLEAN attribute "OSP" for band 1
        String defBody = "{\"name\":\"OSP\",\"type\":\"BOOLEAN\",\"required\":false,"
                + "\"displayInList\":false,\"displayOrder\":0,\"options\":null}";
        MvcResult defResult = mvc.perform(post("/api/bands/1/attribute-defs")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defBody))
                .andExpect(status().isCreated())
                .andReturn();
        String defJson = defResult.getResponse().getContentAsString();
        int idStart = defJson.indexOf("\"id\":") + 5;
        int idEnd = defJson.indexOf(",", idStart);
        if (idEnd == -1) idEnd = defJson.indexOf("}", idStart);
        Long attrId = Long.parseLong(defJson.substring(idStart, idEnd).trim());

        // 2) set OSP=true for member 1 (Jan)
        mvc.perform(post("/api/bands/1/attribute-defs/" + attrId + "/members/1")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"true\"}"))
                .andExpect(status().isOk());

        // 3) render dashboard and assert the BOOLEAN attribute section appears.
        //    (The exact count per attribute is covered by MemberAttributeQueryServiceTest.)
        MvcResult dash = mvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andReturn();
        String html = dash.getResponse().getContentAsString();

        assertThat(html).contains("OSP")
                .withFailMessage("Expected dashboard to show the OSP boolean-attribute section, got:\n" + html);
    }
}
