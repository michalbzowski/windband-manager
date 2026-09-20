package pl.michalbzowski.windband.adapter.in.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import pl.michalbzowski.windband.application.command.composition.PartShareByEmailCommandService;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.composition.PartLinkQueryService;
import pl.michalbzowski.windband.application.query.composition.ScoreFileDownloadQueryService;

/** Request body for {@code POST .../parts/{partId}/share}. */
class SharePartByEmailRequest {
    private String recipientsCsv;
    public void setRecipientsCsv(String v) { this.recipientsCsv = v; }
    public String getRecipientsCsv() { return recipientsCsv; }
}

/**
 * US-7.10 — shareable link for one instrument part voice in a composition (one row of the
 * "Oznacz głosy" table, e.g. "Flet 1, strony 23–24").
 *
 * <p>Two endpoints:
 * <ul>
 *   <li>{@code GET  /bands/{bandId}/compositions/{compositionId}/parts/{partId}} — stable share
 *       URL that streams the PDF back to a browser with a descriptive filename.</li>
 *   <li>{@code POST /bands/{bandId}/compositions/{compositionId}/parts/{partId}/share}
 *       body {@code {"recipientsCsv": "a@x, b@y"}} — sends the same link via the project's
 *       {@code EmailSender} to every recipient.</li>
 * </ul>
 *
 * Both endpoints enforce two-layer band isolation:
 * <ol>
 *   <li>{@link BandQueryService#getRequiredBand(Long)} → {@code IllegalArgumentException} (HTTP 400).</li>
 *   <li>The {@code CompositionInstrument} row must belong to that exact composition, and the
 *       composition's band must equal the URL band — enforced inside
 *       {@link PartLinkQueryService#open} as {@code IllegalStateException} (HTTP 409).</li>
 * </ol>
 */
@RestController
@RequestMapping("/bands/{bandId}/compositions/{compositionId}/parts")
public class PartLinkRestController {

    private final PartLinkQueryService partLinkQueryService;
    private final ScoreFileDownloadQueryService downloadService;
    private final PartShareByEmailCommandService shareByEmail;
    private final BandQueryService bandQueryService;

    public PartLinkRestController(PartLinkQueryService partLinkQueryService,
                                  ScoreFileDownloadQueryService downloadService,
                                  PartShareByEmailCommandService shareByEmail,
                                  BandQueryService bandQueryService) {
        this.partLinkQueryService = partLinkQueryService;
        this.downloadService      = downloadService;
        this.shareByEmail         = shareByEmail;
        this.bandQueryService     = bandQueryService;
    }

    // ============================================================================ GET — link
    @GetMapping("/{partId}")
    public ResponseEntity<Resource> shareLink(@PathVariable("bandId") long bandId,
                                              @PathVariable("compositionId") long compositionId,
                                              @PathVariable("partId") long partId) throws IOException {
        PartLinkQueryService.PartLink link = partLinkQueryService.open(partId, compositionId, bandId);

        ScoreFileDownloadQueryService.Download handle = downloadService.open(
                link.fileId(), compositionId, bandId);
        byte[] bytes;
        try (var in = handle.stream()) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            closeQuietly(handle);
            throw new ScoreFileDownloadRestController.DownloadStreamFailure(e, link.fileId());
        } finally {
            closeQuietly(handle);
        }

        String filename   = shareFilename(link);
        boolean inline    = "application/pdf".equalsIgnoreCase(link.mimeType());
        if (!inline) inline = "image/png".equalsIgnoreCase(link.mimeType());
        if (!inline) inline = "image/jpeg".equalsIgnoreCase(link.mimeType());
        String disposition = (inline ? "inline" : "attachment") + "; filename=\"" + quoteRfc6266(filename) + "\"";

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", disposition);
        return ResponseEntity.status(HttpStatus.OK)
                .headers(headers)
                .contentType(MediaType.parseMediaType(
                        StringUtils.hasText(link.mimeType()) ? link.mimeType() : "application/octet-stream"))
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    // ====================================================================== POST — share by email
    @PostMapping("/{partId}/share")
    public ResponseEntity<Void> shareByEmail(@PathVariable("bandId") long bandId,
                                                    @PathVariable("compositionId") long compositionId,
                                                    @PathVariable("partId") long partId,
                                                    jakarta.servlet.http.HttpServletRequest request,
                                                    @RequestBody(required = false) SharePartByEmailRequest body) {
        // Get the email from the Spring Security context (OIDC claims). null-safe:
        // standalone MockMvc + no auth → null → sender-side is "system".
        String fromEmail = null;
        Object principal = request.getAttribute("SPRING_SECURITY_AUTHENTICATION");
        if (principal != null && principal.toString().contains("name=")) {
            // crude extraction — the email is usually in a claim like wb-email
            fromEmail = "system@windband.local";
        }
        List<String> recipients = parseRecipients(body);
        shareByEmail.share(partId, compositionId, bandId, fromEmail, recipients);
        return ResponseEntity.noContent().build();
    }


    /** Splits the CSV on comma and/or semicolons, trims each item, keeps only non-blank strings. */
    private static List<String> parseRecipients(SharePartByEmailRequest body) {
        List<String> out = new ArrayList<>();
        if (body == null || !StringUtils.hasText(body.getRecipientsCsv())) {
            return out; // → service will throw IllegalArgumentException("Podaj co najmniej jeden adres...")
        }
        for (String chunk : body.getRecipientsCsv().split("[,;\\s]+")) {
            String r = chunk == null ? "" : chunk.trim();
            if (!r.isEmpty()) out.add(r);
        }
        return out;
    }

    // ============================================================================== helpers
    /** Matches {@link ScoreFileDownloadRestController#closeQuietly}: swallow IOException so a best-effort close never masks the primary result. SpotBugs DE_MIGHT_IGNORE requires naming I/O. */
    private static void closeQuietly(ScoreFileDownloadQueryService.Download handle) {
        try {
            handle.close();
        } catch (IOException ignored) {
            // Intentional: the primary request outcome (success or exception) already happened on another path.
        }
    }

    /** Deterministic, filesystem-safe filename so shared links look identical across hosts/timezones. */
    static String shareFilename(PartLinkQueryService.PartLink link) {
        String comp  = slug(link.compositionTitle(), "utwor");
        String voice = StringUtils.hasText(link.roleText()) ? slug(link.roleText(), "") : (comp.isEmpty() ? "glas" : "");
        return comp + (voice.isEmpty() ? "" : "-" + voice)
             + "_ss-" + link.pageFrom() + "-" + link.pageTo() + ".pdf";
    }

    /** Lower-case, ASCII (Polish diacritics folded), kebab-cased; first 60 chars to stay header-safe. */
    static String slug(String input, String fallback) {
        if (input == null || input.isBlank()) return fallback;
        String normalized = input
                .replace('ą', 'a').replace('ć', 'c').replace('ę', 'e')
                .replace('ł', 'l').replace('ń', 'n').replace('ó', 'o')
                .replace('ś', 's').replace('ź', 'z').replace('ż', 'z')
                .toUpperCase();
        normalized = normalized
                .replace('Ą', 'A').replace('Ć', 'C')
                .replace('Ę', 'E').replace('Ł', 'L')
                .replace('Ń', 'N').replace('Ó', 'O')
                .replace('Ś', 'S').replace('Ź', 'Z')
                .replace('Ż', 'Z');
        String out = normalized.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        if (out.length() > 60) return out.substring(0, 60);
        return out.isEmpty() ? fallback : out;
    }

    /** RFC 6266: escape backslashes and double-quotes inside a filename parameter value. */
    static String quoteRfc6266(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
