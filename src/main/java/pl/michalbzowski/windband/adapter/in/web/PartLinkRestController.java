package pl.michalbzowski.windband.adapter.in.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.michalbzowski.windband.application.command.composition.PartShareByEmailCommandService;
import pl.michalbzowski.windband.application.command.composition.PartShareTokenCommandService;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.composition.PartLinkQueryService;

/** Request body for {@code POST .../parts/{partId}/share}. */
class SharePartByEmailRequest {
    private String recipientsCsv;
    public void setRecipientsCsv(String v) { this.recipientsCsv = v; }
    public String getRecipientsCsv() { return recipientsCsv; }
}

/**
 * US-7.10 / US-7.11 — authenticated surface of the per-voice share feature.
 *
 * <p>Endpoints (all require a logged-in band member; the PUBLIC, anonymous read lives in
 * {@link PublicPartLinkRestController}):
 * <ul>
 *   <li>{@code GET  .../parts/{partId}/token} — the voice's live share token, minted on first
 *       call (US-7.11: replaces the old whole-PDF {@code GET .../parts/{partId}} which was
 *       enumerable by band/composition id and leaked the full score instead of the range).</li>
 *   <li>{@code POST .../parts/{partId}/token/rotate} — revoke &amp; re-mint; the previously
 *       distributed link stops resolving immediately.</li>
 *   <li>{@code POST .../parts/{partId}/share} body {@code {"recipientsCsv":"a@x,b@y"}} —
 *       e-mails the token link via the project's {@code EmailSender}.</li>
 * </ul>
 *
 * Band isolation follows the project two-layer contract inside {@link PartLinkQueryService#open}:
 * unknown band → 400; foreign part / composition mismatch → 409.
 */
@RestController
@RequestMapping("/bands/{bandId}/compositions/{compositionId}/parts")
public class PartLinkRestController {

    private final PartLinkQueryService partLinkQueryService;
    private final PartShareByEmailCommandService shareByEmail;
    private final PartShareTokenCommandService tokenService;
    private final BandQueryService bandQueryService;

    public PartLinkRestController(PartLinkQueryService partLinkQueryService,
                                  PartShareByEmailCommandService shareByEmail,
                                  PartShareTokenCommandService tokenService,
                                  BandQueryService bandQueryService) {
        this.partLinkQueryService = partLinkQueryService;
        this.shareByEmail         = shareByEmail;
        this.tokenService         = tokenService;
        this.bandQueryService     = bandQueryService;
    }

    // ==================================================================== GET — legacy tombstone
    /**
     * US-7.11 AC4 — the old enumerable whole-PDF endpoint is GONE. The path remains mapped only
     * as a 410 Gone tombstone so a musician clicking a link e-mailed before the migration gets a
     * clear signal instead of a generic server error. Nothing is ever streamed here.
     */
    @GetMapping("/{partId}")
    public ResponseEntity<Map<String, Object>> legacyPartUrl(@PathVariable("partId") long partId) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.GONE).body(Map.of(
                "error", "Stary link do głosu wygasł — poproś dyrygenta o nowy link (bez logowania)."));
    }

    // ============================================================================ GET — token
    /** Returns {"token": "..."} for the share modal; idempotent (mints once, then stable). */
    @GetMapping("/{partId}/token")
    public ResponseEntity<Map<String, Object>> shareToken(@PathVariable("bandId") long bandId,
                                                          @PathVariable("compositionId") long compositionId,
                                                          @PathVariable("partId") long partId) {
        guardOwnership(partId, compositionId, bandId);
        UUID token = tokenService.tokenFor(partId, "band:" + bandId);
        return ResponseEntity.ok(Map.of("token", token.toString()));
    }

    // =================================================================== POST — rotate token
    @PostMapping("/{partId}/token/rotate")
    public ResponseEntity<Map<String, Object>> rotateToken(@PathVariable("bandId") long bandId,
                                                           @PathVariable("compositionId") long compositionId,
                                                           @PathVariable("partId") long partId) {
        guardOwnership(partId, compositionId, bandId);
        UUID token = tokenService.rotate(partId, "band:" + bandId);
        return ResponseEntity.ok(Map.of("token", token.toString()));
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

    // ============================================================================== guards
    /**
     * Two-layer band isolation reused by the token endpoints. The full resolve also proves a
     * covering PDF exists — handing out a token for a part nobody can serve yet would mint a
     * dead link, so we run the same check the old GET did (NoCoveringFileException → 409).
     */
    private void guardOwnership(long partId, long compositionId, long bandId) {
        bandQueryService.getRequiredBand(bandId);            // layer 1 → 400
        partLinkQueryService.requireOpenable(partId, compositionId, bandId); // layer 2+3 → 409
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
    /**
     * Deterministic, filesystem-safe filename so shared links look identical across hosts/timezones.
     * Public (module) visibility: {@link PublicPartLinkRestController} renders the same name for
     * the token-served slice.
     */
    static String shareFilename(String title, String role, int pageFrom, int pageTo) {
        String comp  = slug(title, "utwor");
        String voice = StringUtils.hasText(role) ? slug(role, "") : (comp.isEmpty() ? "glas" : "");
        return comp + (voice.isEmpty() ? "" : "-" + voice)
             + "_ss-" + pageFrom + "-" + pageTo + ".pdf";
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
                .replace('Ś', 'S').replace('Ź', 'Z').replace('Ż', 'Z');
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
