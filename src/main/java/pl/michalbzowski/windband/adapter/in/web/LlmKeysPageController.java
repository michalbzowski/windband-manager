package pl.michalbzowski.windband.adapter.in.web;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.dto.LlmApiKeySummary;
import pl.michalbzowski.windband.application.query.llmkey.LlmApiKeyQueryService;

/**
 * Strona „Klucze AI” (sekcja Konto w menu): lista zapisanych kluczy LLM
 * użytkownika + formularz dodania/nadpisania klucza OpenRouter.
 */
@Controller
@RequestMapping("/llm-keys")
public class LlmKeysPageController {

    private static final DateTimeFormatter UPDATED_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final LlmApiKeyQueryService queryService;

    public LlmKeysPageController(LlmApiKeyQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public String page(@AuthenticationPrincipal OidcUser principal, Model model) {
        model.addAttribute("llmKeys", rowsOf(principal));
        return "llmkeys/list";
    }

    /** Fragment dla odświeżenia listy po zapisie/usunięciu klucza (htmx). */
    @GetMapping("/list")
    public String listFragment(@AuthenticationPrincipal OidcUser principal, Model model) {
        model.addAttribute("llmKeys", rowsOf(principal));
        return "llmkeys/list :: #llmkeys-content";
    }

    private List<LlmKeyRow> rowsOf(OidcUser principal) {
        Long userId = principal instanceof WindbandOidcUser wu ? wu.getUserId() : null;
        if (userId == null) {
            return List.of();
        }
        return queryService.list(userId).stream().map(LlmKeyRow::from).toList();
    }

    /** Wiersz widoku: data sformatowana po stronie serwera (Instant → strefa systemowa). */
    public record LlmKeyRow(Long id, String provider, String label, String keyHint, String updatedLabel) {

        static LlmKeyRow from(LlmApiKeySummary s) {
            return new LlmKeyRow(s.id(), s.provider(), s.label(), s.keyHint(),
                    s.updatedAt() == null ? "" : UPDATED_FMT.format(s.updatedAt()));
        }
    }
}
