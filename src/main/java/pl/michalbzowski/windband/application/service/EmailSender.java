package pl.michalbzowski.windband.application.service;

/**
 * Outbound port for sending transactional HTML emails.
 *
 * <p>Implemented in the adapter layer (e.g. via SendGrid REST API on port 443,
 * because outbound SMTP is blocked on the hosting platform). Keeping this as an
 * application-layer port avoids leaking {@code org.springframework.web} types
 * (such as {@code RestTemplate}) into the application service layer.
 */
public interface EmailSender {

    /**
     * Sends an HTML email.
     *
     * @param toEmail   recipient address
     * @param toName    recipient display name (may be null)
     * @param subject   email subject
     * @param htmlContent rendered HTML body
     * @throws RuntimeException if delivery fails
     */
    void sendHtmlEmail(String toEmail, String toName, String subject, String htmlContent);
}
