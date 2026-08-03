package pl.michalbzowski.windband.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

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

    // === Resend Welcome Email Scenarios ===

    @Test
    void shouldResendWelcomeEmailWhenAllConsentsAlreadyGranted() {
        // Given: member has all consents already granted
        for (ConsentType type : ConsentType.values()) {
            Consent existing = Consent.create(member, type);
            existing.grant();
            when(consentRepository.findByMemberAndConsentType(member, type))
                    .thenReturn(Optional.of(existing));
        }

        ConsentToken mockToken = mock(ConsentToken.class);
        when(mockToken.getToken()).thenReturn(UUID.randomUUID());
        when(tokenRepository.findByMember(any(Member.class))).thenReturn(Optional.of(mockToken));

        // When
        welcomeService.sendWelcomeIfNeeded(member, "Test Band", currentUser);

        // Then: email is still sent (resend), but no new consent rows created
        verify(emailSender, atLeastOnce()).sendHtmlEmail(eq("user@example.com"), any(), any(), any());
        verify(consentRepository, times(ConsentType.values().length))
                .findByMemberAndConsentType(any(Member.class), any(ConsentType.class));
        verify(templateEngine).process(eq("email/member-welcome"), any());
    }

    @Test
    void shouldResendWelcomeEmailWhenSomeConsentsGrantedAndSomeMissing() {
        // Given: member has one consent granted, one denied, one missing
        Consent granted = Consent.create(member, ConsentType.EVENTS);
        granted.grant();
        when(consentRepository.findByMemberAndConsentType(member, ConsentType.EVENTS))
                .thenReturn(Optional.of(granted));

        Consent denied = Consent.create(member, ConsentType.MANAGER_MESSAGES);
        denied.deny();
        when(consentRepository.findByMemberAndConsentType(member, ConsentType.MANAGER_MESSAGES))
                .thenReturn(Optional.of(denied));

        // Third consent type is missing - should be created with default false
        when(consentRepository.findByMemberAndConsentType(member, ConsentType.INVENTORY_SUMMARY))
                .thenReturn(Optional.empty());

        ConsentToken mockToken = mock(ConsentToken.class);
        when(mockToken.getToken()).thenReturn(UUID.randomUUID());
        when(tokenRepository.findByMember(any(Member.class))).thenReturn(Optional.of(mockToken));

        // When
        welcomeService.sendWelcomeIfNeeded(member, "Test Band", currentUser);

        // Then: email sent, missing consent created (not overwritten granted/denied)
        verify(emailSender, atLeastOnce()).sendHtmlEmail(eq("user@example.com"), any(), any(), any());
        verify(consentRepository, times(3))
                .findByMemberAndConsentType(any(Member.class), any(ConsentType.class));
        // Verify the missing consent was created
        verify(consentRepository).save(argThat(c ->
                c.getConsentType() == ConsentType.INVENTORY_SUMMARY &&
                c.getMember() == member &&
                !c.isGranted()));
    }

    @Test
    void shouldResendWelcomeEmailWhenZeroConsentsExist() {
        // Given: no consent entries at all (first time)
        when(consentRepository.findByMemberAndConsentType(any(Member.class), any(ConsentType.class)))
                .thenReturn(Optional.empty());

        ConsentToken mockToken = mock(ConsentToken.class);
        when(mockToken.getToken()).thenReturn(UUID.randomUUID());
        when(tokenRepository.findByMember(any(Member.class))).thenReturn(Optional.of(mockToken));

        // When
        welcomeService.sendWelcomeIfNeeded(member, "Test Band", currentUser);

        // Then: email sent, all three consent entries created with default false
        verify(emailSender, atLeastOnce()).sendHtmlEmail(eq("user@example.com"), any(), any(), any());
        verify(consentRepository, times(ConsentType.values().length))
                .findByMemberAndConsentType(any(Member.class), any(ConsentType.class));
        verify(consentRepository, times(ConsentType.values().length))
                .save(argThat(c -> c.getMember() == member && !c.isGranted()));
    }

    @Test
    void shouldReuseExistingConsentTokenWhenValid() {
        // Given: existing valid token
        ConsentToken existingToken = mock(ConsentToken.class);
        UUID tokenValue = UUID.randomUUID();
        lenient().when(existingToken.getToken()).thenReturn(tokenValue);
        lenient().when(existingToken.isExpired()).thenReturn(false);
        lenient().when(tokenRepository.findByMember(any(Member.class))).thenReturn(Optional.of(existingToken));

        when(consentRepository.findByMemberAndConsentType(any(Member.class), any(ConsentType.class)))
                .thenReturn(Optional.empty());

        // When
        welcomeService.sendWelcomeIfNeeded(member, "Test Band", currentUser);

        // Then: existing token reused (no new token created)
        verify(emailSender, atLeastOnce()).sendHtmlEmail(eq("user@example.com"), any(), any(), any());
        verify(tokenRepository, never()).save(any(ConsentToken.class));
        // Verify consent link uses existing token
        verify(templateEngine).process(eq("email/member-welcome"),
                argThat(ctx -> ctx.getVariable("consentLink").toString().contains(tokenValue.toString())));
    }

    @Test
    void shouldCreateNewTokenWhenExistingTokenExpired() {
        // Given: existing token but expired - the filter in the service will reject it
        // so findByMember returns the expired token, but filter makes it empty
        ConsentToken expiredToken = mock(ConsentToken.class);
        lenient().when(expiredToken.isExpired()).thenReturn(true);
        lenient().when(expiredToken.getExpiresAt()).thenReturn(Instant.now().minusSeconds(10));
        lenient().when(tokenRepository.findByMember(any(Member.class))).thenReturn(Optional.of(expiredToken));

        ConsentToken newToken = mock(ConsentToken.class);
        UUID newTokenValue = UUID.randomUUID();
        lenient().when(newToken.getToken()).thenReturn(newTokenValue);
        lenient().when(tokenRepository.save(any(ConsentToken.class))).thenReturn(newToken);

        when(consentRepository.findByMemberAndConsentType(any(Member.class), any(ConsentType.class)))
                .thenReturn(Optional.empty());

        // When
        welcomeService.sendWelcomeIfNeeded(member, "Test Band", currentUser);

        // Then: new token created and used
        verify(emailSender, atLeastOnce()).sendHtmlEmail(eq("user@example.com"), any(), any(), any());
        verify(tokenRepository).save(any(ConsentToken.class));
        verify(templateEngine).process(eq("email/member-welcome"),
                argThat(ctx -> ctx.getVariable("consentLink").toString().contains(newTokenValue.toString())));
    }
}
