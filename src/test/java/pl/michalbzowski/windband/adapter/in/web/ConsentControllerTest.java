package pl.michalbzowski.windband.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;

import pl.michalbzowski.windband.application.service.ConsentService;
import pl.michalbzowski.windband.domain.member.ConsentType;

@ExtendWith(MockitoExtension.class)
class ConsentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ConsentService consentService;

    @InjectMocks
    private ConsentController consentController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(consentController).build();
    }

    @Test
    void shouldShowConsentFormWhenTokenValid() throws Exception {
        // given
        String token = UUID.randomUUID().toString();
        when(consentService.getConsentTokenByToken(any(UUID.class))).thenReturn(mock(pl.michalbzowski.windband.domain.member.ConsentToken.class));

        // when
        mockMvc.perform(get("/consent")
                .param("token", token))
                .thenExpect(status().isOk())
                .thenExpect(view().name("consent"))
                .thenExpect(model().attributeExists("token"))
                .thenExpect(model().attributeExists("memberName"))
                .thenExpect(model().attributeExists("teamName"))
                .thenExpect(model().attributeExists("consentTypes"))
                .thenExpect(model().attributeExists("consentMap"));
    }

    @Test
    void shouldReturnBadRequestWhenTokenInvalid() throws Exception {
        // given
        String token = "invalid-token";
        when(consentService.getConsentTokenByToken(any(UUID.class))).thenThrow(new IllegalArgumentException("Invalid"));

        // when/then
        mockMvc.perform(get("/consent")
                .param("token", token))
                .thenExpect(status().isBadRequest());
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
                .thenExpect(status().is3xxRedirection())
                .thenExpect(header().string("Location", contains("/consent?token=" + token + "&saved=true")));

        // then
        verify(consentService).updateConsents(eq(UUID.fromString(token)), eq(ConsentType.EVENTS), eq(true));
    }
}