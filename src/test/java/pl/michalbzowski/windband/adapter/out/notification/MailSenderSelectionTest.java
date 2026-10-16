package pl.michalbzowski.windband.adapter.out.notification;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Transport selection for the {@code EmailSender} port: with
 * {@code app.mail.transport=smtp} (env {@code APP_MAIL_TRANSPORT}) the SMTP
 * sender must be active and the SendGrid API sender absent — never both at
 * once, so that {@code MemberWelcomeService}, {@code EventPartDeliveryCommandService}
 * and {@code PartShareByEmailCommandService} all resolve one unambiguous bean.
 */
class MailSenderSelectionTest {

    private AnnotationConfigApplicationContext startContext(boolean smtpTransport) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (smtpTransport) {
            context.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("test", Collections.singletonMap("app.mail.transport", "smtp")));
        }
        context.register(Infrastructure.class, SendGridEmailSender.class, SmtpEmailSender.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    static class Infrastructure {
        @Bean
        RestTemplate restTemplate() {
            return new RestTemplate();
        }

        @Bean
        JavaMailSender javaMailSender() {
            return Mockito.mock(JavaMailSender.class);
        }
    }

    @Test
    void sendGridApiSenderIsDefault() {
        try (AnnotationConfigApplicationContext context = startContext(false)) {
            assertThat(context.getBeansOfType(SendGridEmailSender.class)).hasSize(1);
            assertThat(context.getBeansOfType(SmtpEmailSender.class)).isEmpty();
        }
    }

    @Test
    void smtpPropertySelectsSmtpSenderOnly() {
        try (AnnotationConfigApplicationContext context = startContext(true)) {
            assertThat(context.getBeansOfType(SmtpEmailSender.class)).hasSize(1);
            assertThat(context.getBeansOfType(SendGridEmailSender.class)).isEmpty();
        }
    }
}
