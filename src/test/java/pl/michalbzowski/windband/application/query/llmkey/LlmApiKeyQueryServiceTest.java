package pl.michalbzowski.windband.application.query.llmkey;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.michalbzowski.windband.adapter.out.crypto.AesGcmSecretCipher;
import pl.michalbzowski.windband.application.security.SecretCipher;
import pl.michalbzowski.windband.domain.llmkey.LlmApiKey;
import pl.michalbzowski.windband.domain.llmkey.LlmApiKeyRepository;
import pl.michalbzowski.windband.domain.llmkey.LlmProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Odczyt skarbca: lista bez sekretów (tylko keyHint) oraz jawny klucz wyłącznie
 * dla właściciela; pusty wynik przy wyłączonym skarbcu / braku klucza.
 */
class LlmApiKeyQueryServiceTest {

    private static String newMasterKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private final SecretCipher cipher = new AesGcmSecretCipher(newMasterKey());
    private final LlmApiKeyRepository repository = mock(LlmApiKeyRepository.class);
    private final LlmApiKeyQueryService service = new LlmApiKeyQueryService(repository, cipher);

    @Test
    @DisplayName("list zwraca podsumowania właściciela bez ciphertext i bez jawnej wartości")
    void listReturnsSummariesWithoutSecrets() {
        LlmApiKey key = LlmApiKey.create(7L, LlmProvider.OPENROUTER, "OpenRouter", "…ab12",
                cipher.encrypt("openrouter-tajny-klucz-ab12"));
        when(repository.findByOwnerId(7L)).thenReturn(List.of(key));

        var summaries = service.list(7L);

        assertThat(summaries).hasSize(1);
        var s = summaries.get(0);
        assertThat(s.provider()).isEqualTo("OPENROUTER");
        assertThat(s.label()).isEqualTo("OpenRouter");
        assertThat(s.keyHint()).isEqualTo("…ab12");
        assertThat(s.updatedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("list(null) → pusta lista (brak zapytania do repo)")
    void listNullOwnerIsEmpty() {
        assertThat(service.list(null)).isEmpty();
    }

    @Test
    @DisplayName("plainKey deszyfruje klucz właściciela (in-memory dla runnera analizy)")
    void plainKeyDecryptsForOwner() {
        LlmApiKey key = LlmApiKey.create(7L, LlmProvider.OPENROUTER, "l", "…ab12",
                cipher.encrypt("openrouter-tajny-klucz-ab12"));
        when(repository.findByOwnerIdAndProvider(7L, LlmProvider.OPENROUTER))
                .thenReturn(Optional.of(key));

        assertThat(service.plainKey(7L, LlmProvider.OPENROUTER))
                .contains("openrouter-tajny-klucz-ab12");
    }

    @Test
    @DisplayName("plainKey: brak klucza / null owner / wyłączony skarbiec → pusto (analiza bez klucza)")
    void plainKeyEmptyWhenUnavailable() {
        when(repository.findByOwnerIdAndProvider(7L, LlmProvider.OPENROUTER))
                .thenReturn(Optional.empty());

        assertThat(service.plainKey(7L, LlmProvider.OPENROUTER)).isEmpty();
        assertThat(service.plainKey(null, LlmProvider.OPENROUTER)).isEmpty();

        LlmApiKeyQueryService disabled = new LlmApiKeyQueryService(
                repository, new AesGcmSecretCipher(null));
        assertThat(disabled.plainKey(7L, LlmProvider.OPENROUTER)).isEmpty();
    }
}
