package pl.michalbzowski.windband.application.command.llmkey;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.michalbzowski.windband.adapter.out.crypto.AesGcmSecretCipher;
import pl.michalbzowski.windband.application.security.SecretCipher;
import pl.michalbzowski.windband.domain.llmkey.LlmApiKey;
import pl.michalbzowski.windband.domain.llmkey.LlmApiKeyRepository;
import pl.michalbzowski.windband.domain.llmkey.LlmProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Skarbiec kluczy LLM — kontrakt zapisu/usuwania: szyfrowanie przed zapisem,
 * keyHint zamiast jawnej wartości, nadpisywanie (jeden klucz na dostawcę),
 * owner-scoped delete oraz fail-closed przy wyłączonym skarbcu.
 */
class LlmApiKeyCommandServiceTest {

    private static String newMasterKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private final SecretCipher cipher = new AesGcmSecretCipher(newMasterKey());
    private final LlmApiKeyRepository repository = mock(LlmApiKeyRepository.class);
    private final LlmApiKeyCommandService service = new LlmApiKeyCommandService(repository, cipher);

    @Test
    @DisplayName("nowy klucz: zapisuje ciphertext (bez jawnej wartości), keyHint = ostatnie 4 znaki")
    void createsEncryptedRowWithHint() {
        when(repository.findByOwnerIdAndProvider(7L, LlmProvider.OPENROUTER)).thenReturn(Optional.empty());
        when(repository.save(any(LlmApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        var summary = service.upsertOpenRouterKey(7L, null, "  openrouter-tajny-klucz-90ab  ");

        assertThat(summary.label()).isEqualTo("OpenRouter");
        assertThat(summary.keyHint()).isEqualTo("…90ab");
        assertThat(summary.provider()).isEqualTo("OPENROUTER");

        ArgumentCaptor<LlmApiKey> captor = ArgumentCaptor.forClass(LlmApiKey.class);
        verify(repository).save(captor.capture());
        LlmApiKey saved = captor.getValue();
        assertThat(saved.getOwnerId()).isEqualTo(7L);
        assertThat(saved.getProvider()).isEqualTo(LlmProvider.OPENROUTER);
        assertThat(saved.getCiphertext()).doesNotContain("openrouter");
        assertThat(cipher.decrypt(saved.getCiphertext())).isEqualTo("openrouter-tajny-klucz-90ab");
    }

    @Test
    @DisplayName("ponowny zapis nadpisuje TEN SAM wiersz (jeden klucz na owner+provider)")
    void replacesExistingKeyInPlace() {
        LlmApiKey existing = LlmApiKey.create(
                7L, LlmProvider.OPENROUTER, "stara", "…old1", cipher.encrypt("stary-klucz-1"));
        when(repository.findByOwnerIdAndProvider(7L, LlmProvider.OPENROUTER))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(LlmApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        var summary = service.upsertOpenRouterKey(7L, "nowa etykieta", "nowy-klucz-123");

        assertThat(summary.label()).isEqualTo("nowa etykieta");
        assertThat(summary.keyHint()).isEqualTo("…-123");
        assertThat(existing.getLabel()).isEqualTo("nowa etykieta");
        assertThat(cipher.decrypt(existing.getCiphertext())).isEqualTo("nowy-klucz-123");
    }

    @Test
    @DisplayName("pusty / za krótki / za długi klucz oraz za długa etykieta → IllegalArgumentException, brak zapisu")
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> service.upsertOpenRouterKey(7L, null, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pusty");
        assertThatThrownBy(() -> service.upsertOpenRouterKey(7L, null, "krotki"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("za krótki");
        assertThatThrownBy(() -> service.upsertOpenRouterKey(7L, null, "x".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("za długi");
        assertThatThrownBy(() -> service.upsertOpenRouterKey(7L, "e".repeat(101), "poprawny-klucz-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Etykieta");
        assertThatThrownBy(() -> service.upsertOpenRouterKey(null, null, "poprawny-klucz-1"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("wyłączony skarbiec (brak klucza głównego) → LlmVaultDisabledException (fail-closed)")
    void vaultDisabledFailsClosed() {
        LlmApiKeyCommandService disabled =
                new LlmApiKeyCommandService(repository, new AesGcmSecretCipher(null));

        assertThatThrownBy(() -> disabled.upsertOpenRouterKey(7L, null, "openrouter-test-key-1234"))
                .isInstanceOf(LlmApiKeyCommandService.LlmVaultDisabledException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("usuwanie jest owner-scoped: cudzy/nieistniejący id → NoSuchElementException, brak delete")
    void deleteIsOwnerScoped() {
        when(repository.findByIdAndOwnerId(5L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(7L, 5L)).isInstanceOf(NoSuchElementException.class);
        verify(repository, never()).delete(any());

        LlmApiKey mine = LlmApiKey.create(7L, LlmProvider.OPENROUTER, "l", "…abcd", "ct");
        when(repository.findByIdAndOwnerId(6L, 7L)).thenReturn(Optional.of(mine));

        service.delete(7L, 6L);

        verify(repository).delete(mine);
    }
}
