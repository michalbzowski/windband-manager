package pl.michalbzowski.windband.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.thymeleaf.spring6.SpringTemplateEngine;

import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.adapter.out.persistence.member.ConsentSpringDataRepository;
import pl.michalbzowski.windband.adapter.out.persistence.member.ConsentTokenSpringDataRepository;
import pl.michalbzowski.windband.domain.member.ConsentType;
import pl.michalbzowski.windband.domain.member.Member;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class MemberWelcomeServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private SpringTemplateEngine templateEngine;

    @Mock
    private ConsentSpringDataRepository consentRepository;

    @Mock
    private ConsentTokenSpringDataRepository tokenRepository;

    @InjectMocks
    private MemberWelcomeService welcomeService;

    private Member member;
    private WindbandOidcUser currentUser;
    private static final String BASE_URL = "http://example.com";
    private static final String FROM = "test@example.com";

    @BeforeEach
    void setUp() {
        // Initialize service with constructor args
        welcomeService = new MemberWelcomeService(mailSender, templateEngine, consentRepository, tokenRepository, BASE_URL, FROM);

        member = mock(Member.class);
        when(member.getEmail()).thenReturn("user@example.com");
        when(member.getFirstName()).thenReturn("Jan");
        when(member.getLastName()).thenReturn("Kowalski");
        when(member.getBand()).thenReturn(mock(pl.michalbzowski.windband.domain.band.Band.class));
        when(member.getBand().getName()).thenReturn("Test Band");

        currentUser = mock(WindbandOidcUser.class);
        when(currentUser.getUsername()).thenReturn("admin");
    }

    @Test
    void shouldSendWelcomeEmailWhenMemberHasEmail() throws MessagingException {
        // given
        when(consentRepository.findByMemberAndConsentType(any(Member.class), any(ConsentType.class)))
                .thenReturn(Optional.empty()); // no existing consents -> will create new

        // token repository: no existing token, will create new
        when(tokenRepository.findByMember(any(Member.class))).thenReturn(Optional.empty());

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // when
        welcomeService.sendWelcomeIfNeeded(member, currentUser);

        // then
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
        // verify that consent rows were created for each type
        verify(consentRepository, times(ConsentType.values().length)).save(any());
        // verify token saved
        verify(tokenRepository).save(any());
        // verify template processed
        verify(templateEngine).process(eq("email/member-welcome"), any());
    }

    @Test
    void shouldNotSendEmailWhenMemberHasNoEmail() throws MessagingException {
        // given
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
        Consent existing = new Consent(member, ConsentType.EVENTS);
        existing.grant();
        when(consentRepository.findByMemberAndConsentType(member, ConsentType.EVENTS))
                .thenReturn(Optional.of(existing));
        // other types return empty -> will be created
        when(consentRepository.findByMemberAndConsentType(member, ConsentType.MANAGER_MESSAGES))
                .thenReturn(Optional.empty());
        when(consentRepository.findByMemberAndConsentType(member, ConsentType.INVENTORY_SUMMARY))
                .thenReturn(Optional.empty());

        when(tokenRepository.findByMember(any(Member.class))).thenReturn(Optional.empty());

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // when
        welcomeService.sendWelcomeIfNeeded(member, currentUser);

        // then
        // save called for missing types only (2 times) + existing not saved again
        verify(consentRepository, times(2)).save(any());
        verify(consentRepository, never()).save(eq(existing)); // existing not saved again
    }
}