package pl.michalbzowski.windband.adapter.in.web;

import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import pl.michalbzowski.windband.application.query.composition.PublicPartLinkQueryService;

/**
 * US-7.11 — the PUBLIC voice link. {@code GET /public/parts/{token}} streams a real,
 * physically sliced PDF containing exactly the voice's page range. No session, no band id,
 * no composition id in the URL: the UUIDv4 token carries all the authorization, so a leaked
 * link cannot be walked into neighbouring bands' documents.
 *
 * <p>Security contract: the route lives under {@code /public/**} (permitAll in SecurityConfig).
 * Unknown / garbage / rotated tokens resolve to a uniform 404 — a non-UUID path segment is
 * parsed defensively here (never a 500), and
 * {@link PublicPartLinkQueryService.TokenNotFoundException} covers the valid-but-unknown case.
 * No existence oracle, and deliberately not a redirect to login (an anonymous musician is the
 * intended audience).</p>
 *
 * <p>Filename reuses the deterministic slug helper from {@link PartLinkRestController} so the
 * download keeps the same "widzi identycznie na każdym hoście" property.</p>
 */
@RestController
public class PublicPartLinkRestController {

    private final PublicPartLinkQueryService publicPartLinkQueryService;

    public PublicPartLinkRestController(PublicPartLinkQueryService publicPartLinkQueryService) {
        this.publicPartLinkQueryService = publicPartLinkQueryService;
    }

    @GetMapping("/public/parts/{token}")
    public ResponseEntity<Resource> open(@PathVariable("token") String rawToken) {
        UUID token;
        try {
            token = UUID.fromString(rawToken);
        } catch (IllegalArgumentException notAUuid) {
            // Garbage in the credential slot is indistinguishable from an unknown token.
            throw new PublicPartLinkQueryService.TokenNotFoundException();
        }
        PublicPartLinkQueryService.PublicPart part =
                publicPartLinkQueryService.openByToken(token);

        String filename = PartLinkRestController.shareFilename(
                part.compositionTitle(), part.roleText(), part.pageFrom(), part.pageTo());
        String disposition = "inline; filename=\"" + PartLinkRestController.quoteRfc6266(filename) + "\"";

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", disposition);
        headers.setCacheControl("private, no-store");   // a credential-bearing URL must not cache
        return ResponseEntity.status(HttpStatus.OK)
                .headers(headers)
                .contentType(MediaType.parseMediaType(part.mimeType()))
                .contentLength(part.pdfBytes().length)
                .body(new ByteArrayResource(part.pdfBytes()));
    }
}
