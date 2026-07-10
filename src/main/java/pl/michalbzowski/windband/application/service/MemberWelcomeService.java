package pl.michalbzowski.windband.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import jakarta.mail.MessagingException;
import java.time.LocalDate;
import java.time.Instant;

import pl.michalbzowski.windband.application.security.CurrentUser;
import pl.michalbzowski.windband.domain.member.Consent;
import pl.michalbzowski.windband.domain.member.ConsentRepository;
import pl.michalbzowski.windband.domain.member.ConsentToken;
import pl.michalbzowski.windband.domain.member.ConsentTokenRepository;
import pl.michalbzowski.windband.domain.member.ConsentType;
import pl.michalbzowski.windband.domain.member.Member;

@Service
@Slf4j
public class MemberWelcomeService {
    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final ConsentRepository consentRepository;
    private final ConsentTokenRepository consentTokenRepository;
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;
    @Value("${app.mail-from:windband@localhost}")
    private String fromAddress;

    public MemberWelcomeService(JavaMailSender mailSender,
                                SpringTemplateEngine templateEngine,
                                ConsentRepository consentRepository,
                                ConsentTokenRepository consentTokenRepository) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.consentRepository = consentRepository;
        this.consentTokenRepository = consentTokenRepository;
    }

    @Async
    public void sendWelcomeIfNeeded(Member member, CurrentUser currentUser) {
        if (member.getEmail() == null || member.getEmail().isBlank()) {
            return;
        }

        // Ensure consent entries exist (default false)
        for (ConsentType type : ConsentType.values()) {
            consentRepository.findByMemberAndConsentType(member, type)
                    .ifPresentOrElse(
                            c -> {}, // already exists
                            () -> consentRepository.save(Consent.create(member, type))
                    );
        }

        // Get or create a consent token for this member (timeless)
        ConsentToken token = consentTokenRepository.findByMember(member)
                .filter(t -> t.getExpiresAt() == null || !Instant.now().isAfter(t.getExpiresAt()))
                .orElseGet(() -> {
                    ConsentToken newToken = ConsentToken.create(member);
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
            context.setVariable("addedBy", currentUser != null ? currentUser.getName() : "unknown");
            context.setVariable("date", LocalDate.now());
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