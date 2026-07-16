package pl.michalbzowski.windband.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;

import pl.michalbzowski.windband.application.security.CurrentUser;
import pl.michalbzowski.windband.domain.member.Consent;
import pl.michalbzowski.windband.domain.member.ConsentRepository;
import pl.michalbzowski.windband.domain.member.ConsentToken;
import pl.michalbzowski.windband.domain.member.ConsentTokenRepository;
import pl.michalbzowski.windband.domain.member.ConsentType;
import pl.michalbzowski.windband.domain.member.Member;

@ExtendWith(MockitoExtension.class)
class MemberWelcomeServiceTest {

    @Mock
    private EmailSender emailSender;

    @Mock
    private SpringTemplateEngine templateEngine;

    @Mock
    private ConsentRepository consentRepository;

    @Mock
    private ConsentTokenRepository tokenRepository;

    @InjectMocks
    private MemberWelcomeService welcomeService;

    private Member member;
    private CurrentUser currentUser;
    private static final String BASE_URL = "http://example.com";

    @BeforeEach
    void setUp() {
        member = mock(Member.class);
        lenient().when(member.getEmail()).thenReturn("user@example.com");
        lenient().when(member.getFirstName()).thenReturn("Jan");
        lenient().when(member.getLastName()).thenReturn("Kowalski");
        // NOTE: member.getBand() must NOT be touched by sendWelcomeIfNeeded — the real
        // production path passes a detached entity with a lazy band proxy and no session.
        // The service now receives teamName as a String, so getBand() should never be called.

        currentUser = mock(CurrentUser.class);
        lenient().when(currentUser.getName()).thenReturn("admin");
        lenient().when(currentUser.getDisplayName()).thenReturn("Admin User");

        welcomeService = new MemberWelcomeService(emailSender, templateEngine, consentRepository, tokenRepository);
        ReflectionTestUtils.setField(welcomeService, "baseUrl", BASE_URL);

        lenient().when(templateEngine.process(eq("email/member-welcome"), any()))
                .thenReturn("<html><body>Test</body></html>");
        // EmailSender.sendHtmlEmail is a void method — no-op by default
    }

    @Test
    void shouldSendWelcomeEmailWhenMemberHasEmail() {
        when(consentRepository.findByMemberAndConsentType(any(Member.class), any(ConsentType.class)))
                .thenReturn(Optional.empty());

        ConsentToken mockToken = mock(ConsentToken.class);
        when(mockToken.getToken()).thenReturn(UUID.randomUUID());
        when(tokenRepository.findByMember(any(Member.class))).thenReturn(Optional.of(mockToken));

        welcomeService.sendWelcomeIfNeeded(member, "Test Band", currentUser);

        verify(emailSender, atLeastOnce()).sendHtmlEmail(eq("user@example.com"), any(), any(), any());
        verify(consentRepository, times(ConsentType.values().length))
                .findByMemberAndConsentType(any(Member.class), any(ConsentType.class));
        verify(templateEngine).process(eq("email/member-welcome"), any());
        verify(member, never()).getBand();
    }

    @Test
    void shouldNotSendEmailWhenMemberHasNoEmail() {
        when(member.getEmail()).thenReturn("");

        welcomeService.sendWelcomeIfNeeded(member, "Test Band", currentUser);

        verify(emailSender, never()).sendHtmlEmail(any(), any(), any(), any());
        verifyNoInteractions(consentRepository, tokenRepository, templateEngine);
    }

    @Test
    void shouldNotResendConsentRowsIfAlreadyExist() {
        Consent existing = Consent.create(member, ConsentType.EVENTS);
        existing.grant();
        when(consentRepository.findByMemberAndConsentType(member, ConsentType.EVENTS))
                .thenReturn(Optional.of(existing));
        when(consentRepository.findByMemberAndConsentType(member, ConsentType.MANAGER_MESSAGES))
                .thenReturn(Optional.empty());
        when(consentRepository.findByMemberAndConsentType(member, ConsentType.INVENTORY_SUMMARY))
                .thenReturn(Optional.empty());

        ConsentToken mockToken = mock(ConsentToken.class);
        when(mockToken.getToken()).thenReturn(UUID.randomUUID());
        when(tokenRepository.findByMember(any(Member.class))).thenReturn(Optional.of(mockToken));

        welcomeService.sendWelcomeIfNeeded(member, "Test Band", currentUser);

        verify(consentRepository, times(3))
                .findByMemberAndConsentType(any(Member.class), any(ConsentType.class));
    }

    /**
     * Reproduces the production bug: sendWelcomeIfNeeded runs in an async thread with NO
     * Hibernate session, so a lazy member.getBand() would throw LazyInitializationException.
     * The service must rely solely on the pre-resolved teamName String and never touch getBand().
     */
    @Test
    void shouldSendEmailWithoutTouchingLazyBandAssociation() {
        lenient().when(member.getBand()).thenThrow(new RuntimeException(
                "could not initialize proxy [pl.michalbzowski.windband.domain.band.Band#2] - no Session"));

        when(consentRepository.findByMemberAndConsentType(any(Member.class), any(ConsentType.class)))
                .thenReturn(Optional.empty());

        ConsentToken mockToken = mock(ConsentToken.class);
        when(mockToken.getToken()).thenReturn(UUID.randomUUID());
        when(tokenRepository.findByMember(any(Member.class))).thenReturn(Optional.of(mockToken));

        welcomeService.sendWelcomeIfNeeded(member, "Resolved Band Name", currentUser);

        verify(emailSender, atLeastOnce()).sendHtmlEmail(any(), any(), any(), any());
        verify(member, never()).getBand();
    }

    @Test
    void shouldUseProvidedTeamNameWhenBandIsNull() {
        lenient().when(member.getBand()).thenReturn(null);

        when(consentRepository.findByMemberAndConsentType(any(Member.class), any(ConsentType.class)))
                .thenReturn(Optional.empty());

        ConsentToken mockToken = mock(ConsentToken.class);
        when(mockToken.getToken()).thenReturn(UUID.randomUUID());
        when(tokenRepository.findByMember(any(Member.class))).thenReturn(Optional.of(mockToken));

        welcomeService.sendWelcomeIfNeeded(member, "Explicit Team", currentUser);

        verify(emailSender, atLeastOnce()).sendHtmlEmail(any(), any(), any(), any());
        verify(member, never()).getBand();
    }

    @Test
    void shouldNotSendWhenEmailSenderFails() {
        when(consentRepository.findByMemberAndConsentType(any(Member.class), any(ConsentType.class)))
                .thenReturn(Optional.empty());
        ConsentToken mockToken = mock(ConsentToken.class);
        when(mockToken.getToken()).thenReturn(UUID.randomUUID());
        when(tokenRepository.findByMember(any(Member.class))).thenReturn(Optional.of(mockToken));

        // EmailSender throws — must be caught inside the async method (no propagation)
        org.mockito.Mockito.doThrow(new RuntimeException("SendGrid down"))
                .when(emailSender).sendHtmlEmail(any(), any(), any(), any());

        // when / then - must not throw
        welcomeService.sendWelcomeIfNeeded(member, "Test Band", currentUser);

        verify(emailSender, atLeastOnce()).sendHtmlEmail(any(), any(), any(), any());
    }
}
