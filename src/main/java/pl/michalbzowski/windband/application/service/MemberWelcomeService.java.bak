package pl.michalbzowski.windband.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.util.UUID;

import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.adapter.out.persistence.member.ConsentSpringDataRepository;
import pl.michalbzowski.windband.adapter.out.persistence.member.ConsentTokenSpringDataRepository;
import pl.michalbzowski.windband.domain.member.Consent;
import pl.michalbzowski.windband.domain.member.ConsentToken;
import pl.michalbzowski.windband.domain.member.ConsentType;
import pl.michalbzowski.windband.domain.member.Member;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberWelcomeService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final ConsentSpringDataRepository consentRepository;
    private final ConsentTokenSpringDataRepository consentTokenRepository;
    private final String baseUrl;
    private final String fromAddress;

    public MemberWelcomeService(JavaMailSender mailSender,
                                SpringTemplateEngine templateEngine,
                                ConsentSpringDataRepository consentRepository,
                                ConsentTokenSpringDataRepository consentTokenRepository,
                                org.springframework.beans.factory.annotation.Value("${app.base-url:http://localhost:8080}") String baseUrl,
                                org.springframework.beans.factory.annotation.Value("${app.mail-from:windband@localhost}") String fromAddress) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.consentRepository = consentRepository;
        this.consentTokenRepository = consentTokenRepository;
        this.baseUrl = baseUrl;
        this.fromAddress = fromAddress;
    }

    @Async
    public void sendWelcomeIfNeeded(Member member, WindbandOidcUser currentUser) {
        // Only send if member has email
        if (member.getEmail() == null || member.getEmail().isBlank()) {
            return;
        }

        // Ensure consent entries exist (default false)
        for (ConsentType type : ConsentType.values()) {
            consentRepository.findByMemberAndConsentType(member, type)
                    .ifPresentOrElse(
                            c -> {}, // already exists
                            () -> consentRepository.save(new Consent(member, type, false))
                    );
        }

        // Get or create a consent token for this member (timeless)
        ConsentToken token = consentTokenRepository.findByMember(member)
                .map(t -> {
                    // If token expired, generate new one
                    if (t.getExpiresAt() != null && java.time.Instant.now().isAfter(t.getExpiresAt())) {
                        // generate new token
                        t.setToken(java.util.UUID.randomUUID());
                        t.setCreatedAt(java.time.Instant.now());
                        t.setExpiresAt(null); // timeless
                        return consentTokenRepository.save(t);
                    }
                    return t;
                })
                .orElseGet(() -> {
                    ConsentToken newToken = new ConsentToken(member);
                    newToken.setToken(java.util.UUID.randomUUID());
                    newToken.setCreatedAt(java.time.Instant.now());
                    newToken.setExpiresAt(null); // timeless
                    return consentTokenRepository.save(newToken);
                });

        // Build verification link
        String consentLink = String.format("%s/consent?token=%s", baseUrl, token.getToken());

        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");

            var context = new org.thymeleaf.context.Context();
            context.setVariable("memberName", member.getFirstName() + " " + member.getLastName());
            context.setVariable("teamName", member.getBand() != null ? member.getBand().getName() : "unknown team");
            context.setVariable("addedBy", currentUser != null ? currentUser.getUsername() : "unknown");
            context.setVariable("date", java.time.LocalDate.now());
            context.setVariable("consentLink", consentLink);
            context.setVariable("supportEmail", "kontakt@bandmanager.pl");

            String htmlContent = templateEngine.process("email/member-welcome", context);

            helper.setTo(member.getEmail());
            helper.setFrom(fromAddress);
            helper.setSubject("Witaj w zespole! – Twoje zgody na komunikację");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Sent welcome email to {} for team {}", member.getEmail(), member.getBand() != null ? member.getBand().getName() : null);
        } catch (MessagingException e) {
            log.error("Failed to send welcome email to {}", member.getEmail(), e);
        }
    }
}