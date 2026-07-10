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
        member = mock(Member.class);
        lenient().when(member.getEmail()).thenReturn("user@example.com");
        lenient().when(member.getFirstName()).thenReturn("Jan");
        lenient().when(member.getLastName()).thenReturn("Kowalski");
        lenient().when(member.getBand()).thenReturn(mock(pl.michalbzowski.windband.domain.band.Band.class));
        lenient().when(member.getBand().getName()).thenReturn("Test Band");

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
        welcomeService.sendWelcomeIfNeeded(member, currentUser);

        // then
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
        // verify that consent rows were created for each type (or skipped if existing)
        verify(consentRepository, times(ConsentType.values().length)).findByMemberAndConsentType(any(Member.class), any(ConsentType.class));
        // verify template processed
        verify(templateEngine).process(eq("email/member-welcome"), any());
    }

    @Test
    void shouldNotSendEmailWhenMemberHasNoEmail() throws MessagingException {
        // given - member email returns empty string
        when(member.getEmail()).thenReturn("");

        // when
        welcomeService.sendWelcomeIfNeeded(member, currentUser);

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
        welcomeService.sendWelcomeIfNeeded(member, currentUser);

        // then
        // only EVENTS returned existing, MANAGER_MESSAGES i INVENTORY_SUMMARY checked (3 total)
        verify(consentRepository, times(3)).findByMemberAndConsentType(any(Member.class), any(ConsentType.class));
    }
}