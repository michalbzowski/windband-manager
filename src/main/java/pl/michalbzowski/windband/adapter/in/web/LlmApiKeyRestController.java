package pl.michalbzowski.windband.adapter.in.web;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.llmkey.LlmApiKeyCommandService;
import pl.michalbzowski.windband.application.dto.LlmApiKeySummary;
import pl.michalbzowski.windband.application.query.llmkey.LlmApiKeyQueryService;

/**
 * Skarbiec kluczy LLM użytkownika (konto, nie zespół): {@code /api/llm-keys}.
 *
 * <p>Jawna wartość klucza trafia wyłącznie do szyfrowania w
 * {@link LlmApiKeyCommandService}; odpowiedzi zawierają tylko {@code keyHint}.
 * Walidacja wejścia → 400, brak zasobu → 404, wyłączony skarbiec → 503
 * (patrz {@code GlobalExceptionHandler}).
 */
@RestController
@RequestMapping("/api/llm-keys")
public class LlmApiKeyRestController {

    private final LlmApiKeyCommandService commandService;
    private final LlmApiKeyQueryService queryService;

    public LlmApiKeyRestController(LlmApiKeyCommandService commandService,
                                   LlmApiKeyQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<List<LlmApiKeySummary>> list(@AuthenticationPrincipal OidcUser principal) {
        Long userId = userIdOf(principal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(queryService.list(userId));
    }

    /** Zapisuje albo nadpisuje klucz OpenRouter użytkownika (jeden klucz na dostawcę). */
    @PostMapping
    public ResponseEntity<LlmApiKeySummary> upsert(@RequestBody UpsertRequest request,
                                                   @AuthenticationPrincipal OidcUser principal) {
        Long userId = userIdOf(principal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        LlmApiKeySummary summary = commandService.upsertOpenRouterKey(userId, request.label(), request.apiKey());
        return ResponseEntity.ok(summary);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id,
                                       @AuthenticationPrincipal OidcUser principal) {
        Long userId = userIdOf(principal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            commandService.delete(userId, id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private static Long userIdOf(OidcUser principal) {
        return principal instanceof WindbandOidcUser wu ? wu.getUserId() : null;
    }

    record UpsertRequest(String label, String apiKey) {
    }
}
