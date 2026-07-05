package pl.michalbzowski.windband.adapter.out.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import pl.michalbzowski.windband.application.command.event.Channel;
import pl.michalbzowski.windband.application.command.event.ChannelException;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventInvitation;
import pl.michalbzowski.windband.domain.member.Member;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Communication channel that sends event invitations via Brevo (formerly Sendinblue) REST API.
 * Uses HTTPS (port 443) — works on any cloud platform including Railway.
 * <p>
 * Activates by default unless {@code app.mail.transport=smtp} is set.
 */
@Component
@ConditionalOnMissingBean(name = "smtpEmailChannel")
public class BrevoApiChannel implements Channel {

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestTemplate restTemplate;
    private final SpringTemplateEngine templateEngine;
    private final String baseUrl;
    private final String fromAddress;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public BrevoApiChannel(
            RestTemplate restTemplate,
            SpringTemplateEngine templateEngine,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl,
            @Value("${app.mail-from:windband@localhost}") String fromAddress,
            @Value("${brevo.api-key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.templateEngine = templateEngine;
        this.baseUrl = baseUrl;
        this.fromAddress = fromAddress;
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() {
        return "EMAIL";
    }

    @Override
    public void send(EventInvitation invitation, BandEvent event, Member member, String baseUrlIgnored) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ChannelException("Brevo API key not configured — set BREVO_API_KEY environment variable",
                    new IllegalStateException("Missing brevo.api-key"));
        }
        if (member.getEmail() == null || member.getEmail().isBlank()) {
            throw new ChannelException("Member " + member.getId() + " has no email address",
                    new IllegalArgumentException("Email required"));
        }

        try {
            // Log egress IP for debugging
            try {
                String egressIp = restTemplate.getForObject("https://api.ipify.org", String.class);
                System.out.println("[BrevoApiChannel] Egress IP: " + egressIp);
            } catch (Exception ipEx) {
                System.out.println("[BrevoApiChannel] Could not detect egress IP: " + ipEx.getMessage());
            }

            String htmlContent = buildEmailHtml(invitation, event, member);

            ObjectNode body = objectMapper.createObjectNode();

            ObjectNode sender = body.putObject("sender");
            sender.put("email", fromAddress);

            ArrayNode toArray = body.putArray("to");
            ObjectNode recipient = toArray.addObject();
            recipient.put("email", member.getEmail());
            recipient.put("name", member.getFirstName() + " " + member.getLastName());

            body.put("subject", "Zaproszenie: " + event.getName() + " — "
                    + (event.getDate() != null ? event.getDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) : ""));
            body.put("htmlContent", htmlContent);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", apiKey);

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    BREVO_API_URL,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<>() {});

            if (response.getStatusCode() != HttpStatusCode.valueOf(201)) {
                String errorBody = response.getBody() != null ? response.getBody().toString() : "no body";
                throw new ChannelException("Brevo API returned " + response.getStatusCode() + ": " + errorBody,
                        new RuntimeException("HTTP " + response.getStatusCode()));
            }

            System.out.println("[BrevoApiChannel] Email sent to " + member.getEmail()
                    + " (messageId: " + (response.getBody() != null ? response.getBody().get("messageId") : "unknown") + ")");

        } catch (ChannelException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[BrevoApiChannel] Error sending to " + member.getEmail() + ": " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("[BrevoApiChannel] Cause: " + e.getCause().getMessage());
            }
            throw new ChannelException("Failed to send via Brevo API to " + member.getEmail(), e);
        }
    }

    private String buildEmailHtml(EventInvitation invitation, BandEvent event, Member member) {
        String token = URLEncoder.encode(invitation.getToken(), StandardCharsets.UTF_8);
        String baseEventUrl = baseUrl + "/public/events/" + token;
        String confirmUrl = baseEventUrl + "?response=CONFIRMED";
        String declineUrl = baseEventUrl + "?response=DECLINED";
        String laterUrl = baseEventUrl + "?response=LATER";

        String instrumentName = member.getPrimaryInstrument()
                .map(i -> i.getName()).orElse(null);

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

        return templateEngine.process("email/event-invitation", ctx);
    }
}