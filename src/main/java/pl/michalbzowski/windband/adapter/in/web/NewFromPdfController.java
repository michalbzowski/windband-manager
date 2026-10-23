package pl.michalbzowski.windband.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.scoreanalysis.AcceptAiProposalsCommandService;
import pl.michalbzowski.windband.application.command.scoreanalysis.NewFromPdfCommandService;

/**
 * Faza D — "New composition from PDF" (plan goal #2): the page plus its two API steps.
 * <ol>
 *   <li>GET  the form page (upload + optional title);</li>
 *   <li>POST /start   — multipart: draft entry + stored PDF + spawned analysis → ids to poll;</li>
 *   <li>POST /confirm — human-edited metadata + accepted rows → 303 to the composition detail.
 * </ol>
 */
@RestController
@RequestMapping("/bands/{bandId}/compositions/new-from-pdf")
public class NewFromPdfController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final NewFromPdfCommandService commandService;

    public NewFromPdfController(NewFromPdfCommandService commandService) {
        this.commandService = commandService;
    }

    /** The form page (client-side JS drives the two steps below). */
    @GetMapping
    public String page(@PathVariable("bandId") Long bandId) {
        return "compositions/newFromPdf";
    }

    /** POST → 200 {@code {compositionId, fileId, analysisId}} — caller then polls /analysis/latest. */
    @PostMapping("/start")
    public ResponseEntity<String> start(@PathVariable("bandId") Long bandId,
                                        @RequestParam(value = "title", required = false) String title,
                                        @RequestPart("file") MultipartFile file,
                                        @AuthenticationPrincipal OidcUser principal) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Wybierz plik PDF do analizy.");
        }
        String rawContentType = file.getContentType();
        String contentType = (rawContentType == null || rawContentType.isBlank())
                ? "application/octet-stream"
                : rawContentType;
        Long userId = principal instanceof WindbandOidcUser wu ? wu.getUserId() : null;
        NewFromPdfCommandService.StartResult result = commandService.start(
                file.getBytes(), contentType, file.getOriginalFilename(), title, bandId, userId);
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(MAPPER.writeValueAsString(result));
    }

    /** The confirmation payload: human-edited work texts + the accepted proposal rows. */
    public record ConfirmBody(
            Long compositionId,
            String title,
            String composer,
            String arranger,
            java.util.List<AcceptAiProposalsCommandService.AcceptedRow> rows) {}

    /** POST → 303 See Other to {@code /bands/{bandId}/compositions/{id}}. */
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@PathVariable("bandId") Long bandId,
                                        @RequestBody ConfirmBody body,
                                        @AuthenticationPrincipal OidcUser principal) {
        if (body == null || body.compositionId() == null) {
            throw new IllegalArgumentException("compositionId jest wymagane.");
        }
        String verifier = "ai-accept";
        if (principal instanceof WindbandOidcUser wu && wu.getUserId() != null) {
            verifier = String.valueOf(wu.getUserId());
        }
        NewFromPdfCommandService.ConfirmResult result = commandService.confirm(
                body.compositionId(), bandId, body.title(), body.composer(), body.arranger(),
                body.rows(), verifier);
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, "/bands/" + bandId + "/compositions/" + result.compositionId())
                .build();
    }
}
