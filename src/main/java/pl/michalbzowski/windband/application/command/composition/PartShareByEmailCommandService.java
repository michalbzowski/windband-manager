package pl.michalbzowski.windband.application.command.composition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.composition.PartLinkQueryService;
import pl.michalbzowski.windband.application.service.EmailSender;

import java.util.List;

/**
 * US-7.10 — render the part-share template and email it to one or more recipients.
 *
 * <p>Best-effort per envelope: a single bad address does not cancel the rest — unless
 * <em>every</em> recipient fails, in which case we rethrow so the UI can surface "nie udało się".
 * The shared link URL is built from {@code app.base-url} + absolute path; same pattern as the
 * consent-link in {@code MemberWelcomeService}. No {@code org.springframework.web..} here.</p>
 */
@Service
public class PartShareByEmailCommandService {

    private static final Logger log = LoggerFactory.getLogger(PartShareByEmailCommandService.class);

    private final EmailSender emailSender;
    private final SpringTemplateEngine templateEngine;
    private final PartLinkQueryService partLinkQueryService;
    private final BandQueryService bandQueryService;
    private final PartShareTokenCommandService tokenService;
    private final String baseUrl;

    public PartShareByEmailCommandService(EmailSender emailSender,
                                          SpringTemplateEngine templateEngine,
                                          PartLinkQueryService partLinkQueryService,
                                          BandQueryService bandQueryService,
                                          PartShareTokenCommandService tokenService,
                                          @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.emailSender          = emailSender;
        this.templateEngine       = templateEngine;
        this.partLinkQueryService = partLinkQueryService;
        this.bandQueryService     = bandQueryService;
        this.tokenService         = tokenService;
        this.baseUrl              = (baseUrl == null) ? "" : baseUrl.replaceAll("/+$", "");
    }

    /**
     * Resolve the part (band-isolated) and email the same HTML body to every recipient.
     * Best-effort per envelope: failures on N>1 recipients do NOT cancel the rest — they are logged;
     * only when <em>every</em> envelope fails do we rethrow the last error so the UI can surface it.
     *
     * @return number of envelopes the transport actually accepted (0 → caller surfaces error).
     */
    public int share(long partId, long compositionId, long bandId,
                     String fromEmail, List<String> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            throw new IllegalArgumentException("Podaj co najmniej jeden adres e-mail.");
        }

        // Resolve once: the band-isolation gate + page-range check live inside PartLinkQueryService.
        PartLinkQueryService.PartLink link =
                partLinkQueryService.open(partId, compositionId, bandId);
        var band = bandQueryService.getRequiredBand(bandId);

        // US-7.11 — the e-mail carries the PUBLIC token link only. No band/composition/part
        // ids leave the system, so the recipient cannot derive the old enumerable URL either.
        java.util.UUID token = tokenService.tokenFor(partId, "band:" + bandId);
        String partLink = baseUrl + "/public/parts/" + token;

        // Render the Thymeleaf email body ONCE — it has no per-recipient variable (fromEmail, subject
        // are sender-side and identical for all recipients).
        Context ctx = new Context(java.util.Locale.forLanguageTag("pl"));
        ctx.setVariable("bandName",               band.getName());
        ctx.setVariable("compositionTitle",       link.compositionTitle());
        ctx.setVariable("voiceLabel",             voiceLabel(link));
        ctx.setVariable("partLink",               partLink);
        ctx.setVariable("pageFrom",               link.pageFrom());
        ctx.setVariable("pageTo",                 link.pageTo());
        ctx.setVariable("originalName",           link.originalName());
        ctx.setVariable("sizeBytes",              link.sizeBytes());
        String htmlBody = templateEngine.process("email/part-share", ctx);

        String subject = "Głos do " + (link.compositionTitle() == null ? "utworu" : link.compositionTitle())
                       + " — strony " + link.pageFrom() + "–" + link.pageTo();

        int sent = 0;
        RuntimeException lastError = null;
        for (String to : recipients) {
            if (to == null || to.isBlank() || !to.contains("@")) {
                log.warn("Part #{}/{} share: skipping malformed recipient '{}' (composition {})",
                        partId, compositionId, to, compositionId);
                lastError = new IllegalStateException("Niepoprawny adres: " + to);
                continue;
            }
            try {
                emailSender.sendHtmlEmail(to, null, subject, htmlBody);
                sent++;
                log.info("Part #{}/{} share: emailed '<redacted>' (from '{}', composition '{}')",
                        partId, compositionId, to, fromEmail, link.compositionTitle());
            } catch (RuntimeException ex) {
                log.warn("Part #{}/{} share: send to '{}' failed: {}",
                        partId, compositionId, to, ex.getMessage(), ex);
                lastError = ex;
            }
        }

        if (sent == 0 && lastError != null) {
            throw new IllegalStateException(
                    "Nie udało się wysłać żadnego z " + recipients.size() + " e-maili: "
                            + lastError.getMessage(), lastError);
        }
        return sent;
    }

    /** Human-readable voice label: "głos" if the role is empty; otherwise "<role>". */
    private static String voiceLabel(PartLinkQueryService.PartLink link) {
        return (link.roleText() == null || link.roleText().isBlank())
                ? "głos" : link.roleText();
    }
}
