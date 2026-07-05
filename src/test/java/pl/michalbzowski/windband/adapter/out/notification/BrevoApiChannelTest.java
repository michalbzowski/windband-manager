package pl.michalbzowski.windband.adapter.out.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.spring6.SpringTemplateEngine;
import pl.michalbzowski.windband.application.command.event.ChannelException;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.event.*;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.Instrument;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrevoApiChannelTest {

    private static final String API_KEY = "test-api-key-12345";
    private static final String FROM_ADDRESS = "windband@example.com";
    private static final String BASE_URL = "https://app.example.com";

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private SpringTemplateEngine templateEngine;
    @Captor
    private ArgumentCaptor<HttpEntity<String>> requestCaptor;

    private BrevoApiChannel channel;
    private EventInvitation invitation;
    private BandEvent event;
    private Member member;
    private Band band;

    @BeforeEach
    void setUp() {
        channel = new BrevoApiChannel(
                restTemplate, templateEngine, BASE_URL, FROM_ADDRESS, API_KEY);

this.invitation = mock(EventInvitation.class);
        when(invitation.getId()).thenReturn(1L);
        when(invitation.getToken()).thenReturn("abc123token");

        band = mock(Band.class);
        when(band.getId()).thenReturn(1L);

        event = BandEvent.create(
                "Koncert Noworoczny",
                LocalDate.of(2026, 1, 15),
                LocalTime.of(18, 0),
                "Filharmonia Warszawska",
                EventType.CONCERT,
                band,
                PaymentType.FREE,
                null);

        member = mock(Member.class);
        when(member.getId()).thenReturn(42L);
        when(member.getFirstName()).thenReturn("Jan");
        when(member.getLastName()).thenReturn("Kowalski");
        when(member.getEmail()).thenReturn("jan.kowalski@example.com");
        when(member.getPrimaryInstrument()).thenReturn(Optional.empty());
    }

    @Test
    void shouldSendEmailViaBrevoApi() {
        // given
        when(templateEngine.process(anyString(), any())).thenReturn("<html>Zaproszenie</html>");
        when(restTemplate.exchange(
                eq("https://api.brevo.com/v3/smtp/email"),
                eq(HttpMethod.POST),
                any(),
                any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.status(201).body(Map.of("messageId", "msg_123")));

        // when
        channel.send(invitation, event, member, BASE_URL);

        // then
        verify(restTemplate).exchange(
                eq("https://api.brevo.com/v3/smtp/email"),
                eq(HttpMethod.POST),
                requestCaptor.capture(),
                any(org.springframework.core.ParameterizedTypeReference.class));

        HttpEntity<String> request = requestCaptor.getValue();
        assertThat(request.getHeaders().getFirst("api-key")).isEqualTo(API_KEY);
        assertThat(request.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);

        // Verify email content in JSON body
        String body = request.getBody();
        assertThat(body).contains("jan.kowalski@example.com");
        assertThat(body).contains(FROM_ADDRESS);
        assertThat(body).contains("Koncert Noworoczny");
        assertThat(body).contains("Jan Kowalski");
        assertThat(body).contains("<html>Zaproszenie</html>");

        verify(templateEngine).process(eq("email/event-invitation"), any());
    }

    @Test
    void shouldThrowWhenApiKeyNotConfigured() {
        // given
        BrevoApiChannel noKeyChannel = new BrevoApiChannel(
                restTemplate, templateEngine, BASE_URL, FROM_ADDRESS, "");

        // when & then
        assertThatThrownBy(() -> noKeyChannel.send(invitation, event, member, BASE_URL))
                .isInstanceOf(ChannelException.class)
                .hasMessageContaining("Brevo API key not configured");
    }

    @Test
    void shouldThrowWhenMemberHasNoEmail() {
        // given
        when(member.getEmail()).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> channel.send(invitation, event, member, BASE_URL))
                .isInstanceOf(ChannelException.class)
                .hasMessageContaining("no email address");
    }

    @Test
    void shouldThrowWhenMemberEmailIsBlank() {
        // given
        when(member.getEmail()).thenReturn("   ");

        // when & then
        assertThatThrownBy(() -> channel.send(invitation, event, member, BASE_URL))
                .isInstanceOf(ChannelException.class)
                .hasMessageContaining("no email address");
    }

    @Test
    void shouldThrowWhenBrevoApiReturnsError() {
        // given
        when(templateEngine.process(anyString(), any())).thenReturn("<html>Test</html>");
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(),
                any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.status(400).body(Map.of("message", "Bad request")));

        // when & then
        assertThatThrownBy(() -> channel.send(invitation, event, member, BASE_URL))
                .isInstanceOf(ChannelException.class)
                .hasMessageContaining("Brevo API returned 400");
    }

    @Test
    void shouldThrowWhenBrevoApiThrowsException() {
        // given
        when(templateEngine.process(anyString(), any())).thenReturn("<html>Test</html>");
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(),
                any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        // when & then
        assertThatThrownBy(() -> channel.send(invitation, event, member, BASE_URL))
                .isInstanceOf(ChannelException.class)
                .hasMessageContaining("Failed to send via Brevo API");
    }

    @Test
    void shouldIncludeInstrumentNameInEmailWhenPresent() {
        // given
        Instrument instrument = mock(Instrument.class);
        when(instrument.getName()).thenReturn("Trąbka");
        when(member.getPrimaryInstrument()).thenReturn(Optional.of(instrument));
        when(templateEngine.process(anyString(), any())).thenReturn("<html>Zaproszenie z instrumentem</html>");
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(),
                any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.status(201).body(Map.of("messageId", "msg_456")));

        // when
        channel.send(invitation, event, member, BASE_URL);

        // then — ensure template context has instrument name
        ArgumentCaptor<org.thymeleaf.context.Context> ctxCaptor =
                ArgumentCaptor.forClass(org.thymeleaf.context.Context.class);
        verify(templateEngine).process(eq("email/event-invitation"), ctxCaptor.capture());
        assertThat(ctxCaptor.getValue().getVariable("instrumentName")).isEqualTo("Trąbka");
    }

    @Test
    void shouldIncludePaymentAmountForPaidEvents() {
        // given
        event = BandEvent.create(
                "Płatny koncert",
                LocalDate.of(2026, 2, 1),
                null,
                null,
                EventType.CONCERT,
                PaymentType.PAID_SPLIT,
                new BigDecimal("150.00"),
                null,
                null);

        when(templateEngine.process(anyString(), any())).thenReturn("<html>Płatne</html>");
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(),
                any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.status(201).body(Map.of("messageId", "msg_789")));

        // when
        channel.send(invitation, event, member, BASE_URL);

        // then
        ArgumentCaptor<org.thymeleaf.context.Context> ctxCaptor =
                ArgumentCaptor.forClass(org.thymeleaf.context.Context.class);
        verify(templateEngine).process(eq("email/event-invitation"), ctxCaptor.capture());
        assertThat(ctxCaptor.getValue().getVariable("paymentType")).isEqualTo("PAID_SPLIT");
        assertThat(ctxCaptor.getValue().getVariable("paymentAmount")).isNotNull();
    }

    @Test
    void shouldGetNameEmail() {
        assertThat(channel.getName()).isEqualTo("EMAIL");
    }
}