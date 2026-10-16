package pl.michalbzowski.windband.adapter.out.notification;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.application.service.EmailSender;

/**
 * Sends transactional emails over SMTP (JavaMail, STARTTLS on port 587).
 *
 * <p>Enabled when {@code app.mail.transport=smtp} (set via the
 * {@code APP_MAIL_TRANSPORT} environment variable). Choose this on hosts that
 * allow outbound SMTP — e.g. Railway PRO. The companion
 * {@link SendGridEmailSender} stands down while this bean is active, exactly
 * like the invitation-channel pair {@code EmailChannel}/{@code SendGridApiChannel}.
 *
 * <p>Server coordinates come from the standard {@code spring.mail.*}
 * properties (see the {@code spring.mail} block in application.yml / the
 * {@code MAIL_HOST}, {@code MAIL_PORT}, {@code MAIL_USERNAME},
 * {@code MAIL_PASSWORD} environment variables).
 */
@Component("smtpEmailSender")
@ConditionalOnProperty(name = "app.mail.transport", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailSender(JavaMailSender mailSender,
                           @Value("${app.mail-from:windband@localhost}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendHtmlEmail(String toEmail, String toName, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, "Band Manager");
            InternetAddress to = new InternetAddress(toEmail);
            if (toName != null && !toName.isBlank()) {
                to.setPersonal(toName);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("[SmtpEmailSender] Email sent via SMTP to {} (subject: {})", toEmail, subject);
        } catch (Exception e) {
            log.error("[SmtpEmailSender] Failed to send email to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send email via SMTP to " + toEmail, e);
        }
    }
}
