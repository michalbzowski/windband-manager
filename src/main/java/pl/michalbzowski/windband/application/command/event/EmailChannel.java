package pl.michalbzowski.windband.application.command.event;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventInvitation;
import pl.michalbzowski.windband.domain.event.EventParticipation;
import pl.michalbzowski.windband.domain.member.Member;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component("smtpEmailChannel")
@ConditionalOnProperty(name = "app.mail.transport", havingValue = "smtp")
public class EmailChannel implements Channel {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final String baseUrl;
    private final String fromAddress;

    public EmailChannel(JavaMailSender mailSender,
                        SpringTemplateEngine templateEngine,
                        @Value("${app.base-url:http://localhost:8080}") String baseUrl,
                        @Value("${app.mail-from:windband@localhost}") String fromAddress) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.baseUrl = baseUrl;
        this.fromAddress = fromAddress;
    }

    @Override
    public String getName() {
        return "EMAIL";
    }

    @Override
    public void send(EventInvitation invitation, BandEvent event, Member member, EventParticipation participation, String baseUrlIgnored) {
        if (member.getEmail() == null || member.getEmail().isBlank()) {
            throw new ChannelException("Member " + member.getId() + " has no email address",
                    new IllegalArgumentException("Email required"));
        }

        try {
            String token = URLEncoder.encode(invitation.getToken(), StandardCharsets.UTF_8);
            String baseEventUrl = baseUrl + "/public/events/" + token;
            String confirmUrl = baseEventUrl + "?response=CONFIRMED";
            String declineUrl = baseEventUrl + "?response=DECLINED";
            String laterUrl = baseEventUrl + "?response=LATER";

            // Use the event-specific instrument if set on EventParticipation, otherwise fall back to member's primary
            String instrumentName = null;
            if (participation != null && participation.getInstrument() != null) {
                instrumentName = participation.getInstrument().getName();
            } else {
                instrumentName = member.getPrimaryInstrument()
                        .map(i -> i.getName()).orElse(null);
            }

            String paymentTypeDisplay = switch (event.getPaymentType().name()) {
                case "FREE" -> "Granie bezpłatne";
                case "PAID_SPLIT" -> "Płatne — podział między grających";
                case "PAID_TO_TEAM" -> "Płatne — na konto zespołu";
                default -> event.getPaymentType().name();
            };

            String formattedAmount = null;
            if (event.getPaymentAmount() != null && event.getPaymentType().name().startsWith("PAID_")) {
                formattedAmount = NumberFormat.getNumberInstance(Locale.forLanguageTag("pl"))
                        .format(event.getPaymentAmount());
            }

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

            Context ctx = new Context();
            ctx.setVariable("eventName", event.getName());
            ctx.setVariable("eventDate", event.getDate() != null ? event.getDate().format(dateFormatter) : null);
            ctx.setVariable("eventTime", event.getStartTime() != null ? event.getStartTime().toString() : null);
            ctx.setVariable("eventLocation", event.getLocation());
            ctx.setVariable("eventType", event.getEventType().name());
            ctx.setVariable("paymentType", event.getPaymentType().name());
            ctx.setVariable("paymentTypeDisplay", paymentTypeDisplay);
            ctx.setVariable("paymentAmount", formattedAmount);
            ctx.setVariable("notes", event.getNotes());
            ctx.setVariable("memberName", member.getFirstName() + " " + member.getLastName());
            ctx.setVariable("memberEmail", member.getEmail());
            ctx.setVariable("instrumentName", instrumentName);
            ctx.setVariable("confirmUrl", confirmUrl);
            ctx.setVariable("declineUrl", declineUrl);
            ctx.setVariable("laterUrl", laterUrl);
            ctx.setVariable("eventUrl", baseEventUrl);

            String htmlContent = templateEngine.process("email/event-invitation", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(member.getEmail());
            helper.setFrom(fromAddress);
            helper.setSubject("Zaproszenie: " + event.getName() + " — "
                    + event.getDate().format(dateFormatter));
            helper.setText(htmlContent, true);

            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("[EmailChannel] SMTP error for " + member.getEmail() + ": " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("[EmailChannel] Cause: " + e.getCause().getMessage());
            }
            throw new ChannelException("Failed to send email to " + member.getEmail(), e);
        }
    }
}
