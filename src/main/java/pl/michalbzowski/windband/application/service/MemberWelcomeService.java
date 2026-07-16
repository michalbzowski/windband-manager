package pl.michalbzowski.windband.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import pl.michalbzowski.windband.application.security.CurrentUser;
import pl.michalbzowski.windband.domain.member.Consent;
import pl.michalbzowski.windband.domain.member.ConsentRepository;
import pl.michalbzowski.windband.domain.member.ConsentToken;
import pl.michalbzowski.windband.domain.member.ConsentTokenRepository;
import pl.michalbzowski.windband.domain.member.ConsentType;
import pl.michalbzowski.windband.domain.member.Member;

/**
 * Sends a welcome email (with a consent link) to newly added members.
 *
 * <p>Email delivery is delegated to {@link EmailSender} (implemented by the
 * SendGrid REST API adapter on port 443) because outbound SMTP is blocked on
 * the hosting platform (Railway). The service itself stays free of
 * {@code org.springframework.web} dependencies.
 */
@Service
@Slf4j
public class MemberWelcomeService {
    private final EmailSender emailSender;
    private final SpringTemplateEngine templateEngine;
    private final ConsentRepository consentRepository;
    private final ConsentTokenRepository consentTokenRepository;
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public MemberWelcomeService(EmailSender emailSender,
                                SpringTemplateEngine templateEngine,
                                ConsentRepository consentRepository,
                                ConsentTokenRepository consentTokenRepository) {
        this.emailSender = emailSender;
        this.templateEngine = templateEngine;
        this.consentRepository = consentRepository;
        this.consentTokenRepository = consentTokenRepository;
    }

    /**
     * Sends a welcome email with a consent link if the member has an email address.
     *
     * @param member      the member to welcome (may be a detached/lazy entity — must NOT touch
     *                    lazy associations such as {@code member.getBand()} because this method
     *                    runs in an async thread with no Hibernate session)
     * @param teamName    pre-resolved band name (already materialized by the caller)
     * @param currentUser the user who triggered the action
     */
    @Transactional
    @Async
    public void sendWelcomeIfNeeded(Member member, String teamName, CurrentUser currentUser) {
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
            Context context = new Context();
            context.setVariable("memberName", member.getFirstName() + " " + member.getLastName());
            context.setVariable("teamName", teamName != null ? teamName : "unknown team");
            context.setVariable("addedBy", currentUser != null ? currentUser.getDisplayName() : "unknown");
            context.setVariable("date", LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            context.setVariable("consentLink", consentLink);
            context.setVariable("supportEmail", "kontakt@bandmanager.pl");

            String htmlContent = templateEngine.process("email/member-welcome", context);

            emailSender.sendHtmlEmail(
                    member.getEmail(),
                    member.getFirstName() + " " + member.getLastName(),
                    "Witaj w zespole! – Twoje zgody na komunikację",
                    htmlContent);
            log.info("Sent welcome email to {} for team {}", member.getEmail(), teamName);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}", member.getEmail(), e);
        }
    }
}
