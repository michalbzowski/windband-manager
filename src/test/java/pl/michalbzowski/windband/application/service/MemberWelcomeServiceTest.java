package pl.michalbzowski.windband.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
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
    private JavaMailSender mailSender;

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
    private static final String FROM = "test@example.com";

    @BeforeEach
    void setUp() {
        // given
        member = mock(Member.class);
        lenient().when(member.getEmail()).thenReturn("user@example.com");
        lenient().when(member.getFirstName()).thenReturn("Jan");
        lenient().when(member.getLastName()).thenReturn("Kowalski");
        // NOTE: member.getBand() must NOT be touched by sendWelcomeIfNeeded — the real
        // production path passes a detached entity with a lazy band proxy and no session.
        // The service now receives teamName as a String, so getBand() should never be called.

        currentUser = mock(CurrentUser.class);
        lenient().when(currentUser.getName()).thenReturn("admin");

        welcomeService = new MemberWelcomeService(mailSender, templateEngine, consentRepository, tokenRepository);
        ReflectionTestUtils.setField(welcomeService, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(welcomeService, "fromAddress", FROM);
    }

    @Test
    void shouldSendWelcomeEmailWhenMemberHasEmail() throws MessagingException {
        // given
        when(consentRepository.findByMemberAndConsentType(any(Member.class), any(ConsentType.class)))
                .thenReturn(Optional.empty()); // no existing consents -> will create new

        // token repository: no existing token, will create new
        ConsentToken mockToken = mock(ConsentToken.class);
        when(mockToken.getToken()).thenReturn(UUID.randomUUID());
        when(tokenRepository.findByMember(any(Member.class))).thenReturn(Optional.of(mockToken));

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // mock template engine to return HTML content
        when(templateEngine.process(eq("email/member-welcome"), any())).thenReturn("<html><body>Test</body></html>");

        // when
        welcomeService.sendWelcomeIfNeeded(member, "Test Band", currentUser);

        // then
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
        // verify that consent rows were created for each type (or skipped if existing)
        verify(consentRepository, times(ConsentType.values().length)).findByMemberAndConsentType(any(Member.class), any(ConsentType.class));
        // verify template processed
        verify(templateEngine).process(eq("email/member-welcome"), any());
        // CRITICAL: must not touch the lazy band association (would fail with no Session)
        verify(member, never()).getBand();
    }

    @Test
    void shouldNotSendEmailWhenMemberHasNoEmail() throws MessagingException {
        // given - member email returns empty string
        when(member.getEmail()).thenReturn("");

        // when
        welcomeService.sendWelcomeIfNeeded(member, "Test Band", currentUser);

        // then
        verify(mailSender, never()).send(any(MimeMessage.class));
        verifyNoInteractions(consentRepository, tokenRepository, templateEngine);
    }

    @Test
    void shouldNotResendConsentRowsIfAlreadyExist() throws MessagingException {
        // given
        Consent existing = Consent.create(member, ConsentType.EVENTS);
        existing.grant();
        when(consentRepository.findByMemberAndConsentType(member, ConsentType.EVENTS))
                .thenReturn(Optional.of(existing));
        // other types return empty -> will be created
        when(consentRepository.findByMemberAndConsentType(member, ConsentType.MANAGER_MESSAGES))
                .thenReturn(Optional.empty());
        when(consentRepository.findByMemberAndConsentType(member, ConsentType.INVENTORY_SUMMARY))
                .thenReturn(Optional.empty());

        ConsentToken mockToken = mock(ConsentToken.class);
        when(mockToken.getToken()).thenReturn(UUID.randomUUID());
        when(tokenRepository.findByMember(any(Member.class))).thenReturn(Optional.of(mockToken));

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("email/member-welcome"), any())).thenReturn("<html><body>Test</body></html>");

        // when
        welcomeService.sendWelcomeIfNeeded(member, "Test Band", currentUser);

        // then
        // only EVENTS returned existing, MANAGER_MESSAGES i INVENTORY_SUMMARY checked (3 total)
        verify(consentRepository, times(3)).findByMemberAndConsentType(any(Member.class), any(ConsentType.class));
    }

    /**
     * Reproduces the production bug: sendWelcomeIfNeeded runs in an async thread with NO
     * Hibernate session, so a lazy member.getBand() would throw LazyInitializationException.
     * The service must rely solely on the pre-resolved teamName String and never touch getBand().
     */
    @Test
    void shouldSendEmailWithoutTouchingLazyBandAssociation() throws MessagingException {
        // given - getBand() simulates a detached lazy proxy with no session.
        // lenient() because the whole point is that the service must NOT call getBand().
        lenient().when(member.getBand()).thenThrow(new RuntimeException(
                "could not initialize proxy [pl.michalbzowski.windband.domain.band.Band#2] - no Session"));

        when(consentRepository.findByMemberAndConsentType(any(Member.class), any(ConsentType.class)))
                .thenReturn(Optional.empty());

        ConsentToken mockToken = mock(ConsentToken.class);
        when(mockToken.getToken()).thenReturn(UUID.randomUUID());
        when(tokenRepository.findByMember(any(Member.class))).thenReturn(Optional.of(mockToken));

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("email/member-welcome"), any())).thenReturn("<html><body>Test</body></html>");

        // when / then - must NOT propagate the LazyInitializationException from getBand()
        welcomeService.sendWelcomeIfNeeded(member, "Resolved Band Name", currentUser);

        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
        verify(member, never()).getBand();
    }

    @Test
    void shouldUseProvidedTeamNameWhenBandIsNull() throws MessagingException {
        // given - no band at all, but teamName provided explicitly.
        // lenient() because the service must not call getBand() (uses teamName String instead).
        lenient().when(member.getBand()).thenReturn(null);

        when(consentRepository.findByMemberAndConsentType(any(Member.class), any(ConsentType.class)))
                .thenReturn(Optional.empty());

        ConsentToken mockToken = mock(ConsentToken.class);
        when(mockToken.getToken()).thenReturn(UUID.randomUUID());
        when(tokenRepository.findByMember(any(Member.class))).thenReturn(Optional.of(mockToken));

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("email/member-welcome"), any())).thenReturn("<html><body>Test</body></html>");

        // when
        welcomeService.sendWelcomeIfNeeded(member, "Explicit Team", currentUser);

        // then - template processed with the provided team name, no band access
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
        verify(member, never()).getBand();
    }
}