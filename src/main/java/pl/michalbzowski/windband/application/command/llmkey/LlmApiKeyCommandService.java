package pl.michalbzowski.windband.application.command.llmkey;

import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.michalbzowski.windband.application.dto.LlmApiKeySummary;
import pl.michalbzowski.windband.application.security.SecretCipher;
import pl.michalbzowski.windband.domain.llmkey.LlmApiKey;
import pl.michalbzowski.windband.domain.llmkey.LlmApiKeyRepository;
import pl.michalbzowski.windband.domain.llmkey.LlmProvider;

/**
 * Zapis i usuwanie kluczy API użytkownika (skarbiec LLM).
 *
 * <p>Zasady bezpieczeństwa:
 * <ul>
 *   <li>jawna wartość klucza nigdy nie jest logowana ani zwracana — przed zapisem
 *       szyfrowana AES-GCM ({@link SecretCipher});</li>
 *   <li>UI dostaje wyłącznie {@code keyHint} (ostatnie 4 znaki);</li>
 *   <li>jeden klucz na (użytkownik, dostawca) — ponowny zapis nadpisuje wiersz;</li>
 *   <li>usuwanie zawsze w zakresie właściciela: cudzy {@code keyId} jest
 *       traktowany jak nieistniejący (404), bez ujawniania zasobów innych osób.</li>
 * </ul>
 */
@Service
public class LlmApiKeyCommandService {

    static final int MIN_KEY_LENGTH = 8;
    static final int MAX_KEY_LENGTH = 500;
    static final int MAX_LABEL_LENGTH = 100;

    private final LlmApiKeyRepository repository;
    private final SecretCipher cipher;

    public LlmApiKeyCommandService(LlmApiKeyRepository repository, SecretCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    /**
     * Tworzy albo nadpisuje klucz OPENROUTER użytkownika.
     *
     * @param ownerId  id {@code AppUser} — właściciel klucza
     * @param label    opcjonalna etykieta (domyślnie „OpenRouter”)
     * @param plainKey jawna wartość klucza — użyta wyłącznie do zaszyfrowania
     * @throws IllegalArgumentException pusty/za krótki/za długi klucz lub za długa etykieta (→ 400)
     * @throws LlmVaultDisabledException serwer nie ma klucza głównego (→ 503)
     */
    @Transactional
    public LlmApiKeySummary upsertOpenRouterKey(Long ownerId, String label, String plainKey) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId required");
        }
        if (plainKey == null || plainKey.isBlank()) {
            throw new IllegalArgumentException("Klucz API nie może być pusty.");
        }
        String key = plainKey.trim();
        if (key.length() < MIN_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "Klucz API jest za krótki (minimum " + MIN_KEY_LENGTH + " znaków).");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "Klucz API jest za długi (maksymalnie " + MAX_KEY_LENGTH + " znaków).");
        }
        if (!cipher.isEnabled()) {
            throw new LlmVaultDisabledException();
        }
        String effectiveLabel = label == null || label.isBlank() ? "OpenRouter" : label.trim();
        if (effectiveLabel.length() > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException(
                    "Etykieta jest za długa (maksymalnie " + MAX_LABEL_LENGTH + " znaków).");
        }
        String keyHint = "…" + key.substring(key.length() - 4);
        String ciphertext = cipher.encrypt(key);

        LlmApiKey row = repository.findByOwnerIdAndProvider(ownerId, LlmProvider.OPENROUTER)
                .map(existing -> {
                    existing.replaceWith(effectiveLabel, keyHint, ciphertext);
                    return existing;
                })
                .orElseGet(() -> LlmApiKey.create(
                        ownerId, LlmProvider.OPENROUTER, effectiveLabel, keyHint, ciphertext));
        return LlmApiKeySummary.from(repository.save(row));
    }

    /**
     * Usuwa klucz użytkownika; cudzy lub nieistniejący {@code keyId} →
     * {@link NoSuchElementException} (→ 404).
     */
    @Transactional
    public void delete(Long ownerId, Long keyId) {
        LlmApiKey row = repository.findByIdAndOwnerId(keyId, ownerId)
                .orElseThrow(() -> new NoSuchElementException("Klucz " + keyId + " nie istnieje."));
        repository.delete(row);
    }

    /** Skarbiec wyłączony: brak {@code LLM_KEYS_MASTER_KEY} w środowisku serwera. */
    public static class LlmVaultDisabledException extends IllegalStateException {
        public LlmVaultDisabledException() {
            super("Skarbiec kluczy jest wyłączony — serwer nie ma skonfigurowanego LLM_KEYS_MASTER_KEY.");
        }
    }
}
