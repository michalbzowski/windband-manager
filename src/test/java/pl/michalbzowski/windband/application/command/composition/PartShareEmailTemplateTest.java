package pl.michalbzowski.windband.application.command.composition;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import pl.michalbzowski.windband.BaseIntegrationTest;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Issue #228 — sending a part by e-mail failed with
 * "An error happened during template parsing (template: class path resource
 * [templates/email/part-share.html])".
 *
 * <p>Every existing test mocked {@code SpringTemplateEngine}, so nothing in the suite ever
 * parsed this template and the defect reached production.
 */
class PartShareEmailTemplateTest extends BaseIntegrationTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Test
    void partShareTemplateRenders() {
        Context ctx = new Context(Locale.forLanguageTag("pl"));
        ctx.setVariable("bandName", "Orkiestra Testowa");
        ctx.setVariable("compositionTitle", "Powitanie");
        ctx.setVariable("voiceLabel", "Trąbka 1");
        ctx.setVariable("partLink", "http://localhost:8081/public/parts/00000000-0000-0000-0000-000000000001");
        ctx.setVariable("pageFrom", 3);
        ctx.setVariable("pageTo", 7);
        ctx.setVariable("originalName", "powitanie.pdf");
        ctx.setVariable("sizeBytes", 1_887_436L);

        String[] rendered = new String[1];
        assertThatCode(() -> rendered[0] = templateEngine.process("email/part-share", ctx))
                .as("issue #228: the template threw during parsing")
                .doesNotThrowAnyException();

        String html = rendered[0];
        assertThat(html)
                .as("the page range must read as one range, not a broken expression")
                .contains("3–7");
        assertThat(html)
                .as("the MB conversion must not leak a raw double into the e-mail")
                .containsPattern("(?i)1[.,]8 MB")
                .doesNotContain("1.7978591918945312")
                .doesNotContain("${")
                .doesNotContain("Exception");
        assertThat(html).contains("Otwórz swój głos w przeglądarce");
        assertThat(html).contains("powitanie.pdf").contains("/public/parts/00000000");
    }

    @Test
    void smallPartStillReportsKilobytes() {
        Context ctx = new Context(Locale.forLanguageTag("pl"));
        ctx.setVariable("bandName", "Orkiestra Testowa");
        ctx.setVariable("compositionTitle", "Mały głos");
        ctx.setVariable("voiceLabel", "Flet");
        ctx.setVariable("partLink", "http://localhost:8081/public/parts/x");
        ctx.setVariable("pageFrom", 1);
        ctx.setVariable("pageTo", 1);
        ctx.setVariable("originalName", "flet.pdf");
        ctx.setVariable("sizeBytes", 40_960L);

        String html = templateEngine.process("email/part-share", ctx);
        assertThat(html)
                .as("under a megabyte the size should be in KB, and 1–1 must read as a single page")
                .contains("40 KB")
                .contains("1–1");
    }
}
