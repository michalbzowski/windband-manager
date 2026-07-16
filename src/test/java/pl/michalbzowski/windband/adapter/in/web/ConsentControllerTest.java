package pl.michalbzowski.windband.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import pl.michalbzowski.windband.application.service.ConsentService;
import pl.michalbzowski.windband.application.service.ConsentPageData;
import pl.michalbzowski.windband.domain.member.ConsentType;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConsentControllerTest {

    private MockMvc mockMvc;
    private ConsentService consentService;

    @BeforeEach
    void setUp() {
        consentService = mock(ConsentService.class);
        ConsentController controller = new ConsentController(consentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldShowConsentFormWhenTokenValid() throws Exception {
        // given
        UUID token = UUID.randomUUID();
        Map<ConsentType, Boolean> consentMap = new EnumMap<>(ConsentType.class);
        for (ConsentType type : ConsentType.values()) {
            consentMap.put(type, false);
        }
        ConsentPageData data = new ConsentPageData("Jan Kowalski", "Zespół Testowy", token, consentMap);

        when(consentService.getConsentPageData(any(UUID.class))).thenReturn(data);

        // when
        mockMvc.perform(get("/consent")
                .param("token", token.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("token"))
                .andExpect(model().attributeExists("memberName"))
                .andExpect(model().attributeExists("teamName"))
                .andExpect(model().attributeExists("consentTypes"))
                .andExpect(model().attributeExists("consentMap"));
    }

    @Test
    void shouldReturnBadRequestWhenTokenInvalid() throws Exception {
        // given
        when(consentService.getConsentPageData(any(UUID.class)))
                .thenThrow(new IllegalArgumentException("Invalid token"));

        // when/then
        mockMvc.perform(get("/consent")
                .param("token", "invalid-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdateConsentAndRedirectWhenFormSubmitted() throws Exception {
        // given
        String token = UUID.randomUUID().toString();
        String type = ConsentType.EVENTS.name();
        boolean grant = true;

        // when
        mockMvc.perform(post("/consent")
                .param("token", token)
                .param("type", type)
                .param("grant", String.valueOf(grant)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/consent?**"));
    }
}
