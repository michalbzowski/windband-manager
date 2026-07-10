package pl.michalbzowski.windband.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import pl.michalbzowski.windband.application.service.ConsentService;
import pl.michalbzowski.windband.domain.member.ConsentType;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.member.ConsentToken;

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
        String token = UUID.randomUUID().toString();
        ConsentToken mockToken = mock(ConsentToken.class);
        Member mockMember = mock(Member.class);
        Band mockBand = mock(Band.class);

        when(mockMember.getFirstName()).thenReturn("Jan");
        when(mockMember.getLastName()).thenReturn("Kowalski");
        when(mockMember.getBand()).thenReturn(mockBand);
        when(mockBand.getName()).thenReturn("Zespół Testowy");
        when(mockToken.getMember()).thenReturn(mockMember);
        when(consentService.getConsentTokenByToken(any(UUID.class))).thenReturn(mockToken);
        when(consentService.isConsentGranted(any(), any())).thenReturn(false);

        // when
        mockMvc.perform(get("/consent")
                .param("token", token))
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
        when(consentService.getConsentTokenByToken(any(UUID.class))).thenThrow(new IllegalArgumentException("Invalid token"));

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