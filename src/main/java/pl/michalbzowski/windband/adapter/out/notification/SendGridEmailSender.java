package pl.michalbzowski.windband.adapter.out.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import pl.michalbzowski.windband.application.service.EmailSender;

/**
 * Sends transactional emails via the SendGrid v3 REST API (HTTPS, port 443).
 *
 * <p>Used for messages that must not depend on outbound SMTP (which is blocked on
 * the hosting platform). The companion {@link SendGridApiChannel} covers event
 * invitations through the same transport.
 */
@Component
public class SendGridEmailSender implements EmailSender {

    private static final String SENDGRID_API_URL = "https://api.sendgrid.com/v3/mail/send";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String fromAddress;
    private final String apiKey;

    public SendGridEmailSender(RestTemplate restTemplate,
                               @Value("${app.mail-from:windband@localhost}") String fromAddress,
                               @Value("${sendgrid.api-key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.fromAddress = fromAddress;
        this.apiKey = apiKey;
    }

    @Override
    public void sendHtmlEmail(String toEmail, String toName, String subject, String htmlContent) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "SendGrid API key not configured — set SENDGRID_API_KEY environment variable");
        }

        try {
            ObjectNode body = objectMapper.createObjectNode();

            ObjectNode personalization = body.putArray("personalizations").addObject();
            ObjectNode recipient = personalization.putArray("to").addObject();
            recipient.put("email", toEmail);
            if (toName != null && !toName.isBlank()) {
                recipient.put("name", toName);
            }

            ObjectNode from = body.putObject("from");
            from.put("email", fromAddress);
            from.put("name", "Band Manager");

            body.put("subject", subject);
            body.putArray("content")
                    .add(objectMapper.createObjectNode()
                            .put("type", "text/html")
                            .put("value", htmlContent));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);

            var response = restTemplate.exchange(
                    SENDGRID_API_URL, HttpMethod.POST, request, String.class);

            HttpStatusCode status = response.getStatusCode();
            if (!status.is2xxSuccessful()) {
                throw new RuntimeException("SendGrid API returned " + status + ": " + response.getBody());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email via SendGrid to " + toEmail, e);
        }
    }
}
