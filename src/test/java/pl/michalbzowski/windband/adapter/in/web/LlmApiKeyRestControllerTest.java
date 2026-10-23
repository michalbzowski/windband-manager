package pl.michalbzowski.windband.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.llmkey.LlmApiKeyCommandService;
import pl.michalbzowski.windband.application.dto.LlmApiKeySummary;
import pl.michalbzowski.windband.application.query.llmkey.LlmApiKeyQueryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kontrakt HTTP skarbca kluczy LLM ({@code /api/llm-keys}): wszystko w zakresie
 * zalogowanego użytkownika; brak principal → 401; cudzy/nieistniejący klucz → 404.
 */
class LlmApiKeyRestControllerTest {

    private final LlmApiKeyCommandService command = mock(LlmApiKeyCommandService.class);
    private final LlmApiKeyQueryService query = mock(LlmApiKeyQueryService.class);
    private final LlmApiKeyRestController controller = new LlmApiKeyRestController(command, query);
    private final WindbandOidcUser user = mock(WindbandOidcUser.class);

    @BeforeEach
    void setUp() {
        when(user.getUserId()).thenReturn(42L);
    }

    @Test
    @DisplayName("GET /api/llm-keys → 200 z listą KLUCZY WŁAŚCICIELA (tylko podsumowania)")
    void listReturnsOwnerKeys() {
        var summary = new LlmApiKeySummary(1L, "OPENROUTER", "OpenRouter", "…ab12", Instant.now());
        when(query.list(42L)).thenReturn(List.of(summary));

        var resp = controller.list(user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsExactly(summary);
    }

    @Test
    @DisplayName("POST /api/llm-keys przekazuje jawny klucz wyłącznie do command service (szyfrowanie)")
    void upsertPassesPlainKeyToService() {
        var summary = new LlmApiKeySummary(1L, "OPENROUTER", "moja", "…x890", Instant.now());
        when(command.upsertOpenRouterKey(42L, "moja", "openrouter-fixture-x890")).thenReturn(summary);

        var resp = controller.upsert(
                new LlmApiKeyRestController.UpsertRequest("moja", "openrouter-fixture-x890"), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo(summary);
        verify(command).upsertOpenRouterKey(42L, "moja", "openrouter-fixture-x890");
    }

    @Test
    @DisplayName("DELETE istniejącego klucza → 204; cudzy/nieistniejący id → 404")
    void deleteOwnerScoped() {
        assertThat(controller.delete(1L, user).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(command).delete(42L, 1L);

        doThrow(new NoSuchElementException("Klucz 9 nie istnieje."))
                .when(command).delete(42L, 9L);
        assertThat(controller.delete(9L, user).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("brak uwierzytelnionego użytkownika (null principal) → 401 bez wywoływania serwisów")
    void anonymousPrincipalUnauthorized() {
        assertThat(controller.list(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.upsert(
                new LlmApiKeyRestController.UpsertRequest("l", "k"), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.delete(1L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(query, org.mockito.Mockito.never()).list(anyLong());
        verify(command, org.mockito.Mockito.never()).delete(anyLong(), anyLong());
    }
}
