package pl.michalbzowski.windband.application.dto;

import java.time.Instant;

import pl.michalbzowski.windband.domain.llmkey.LlmApiKey;

/**
 * Widok klucza API użytkownika bez sekretów: tylko {@code keyHint}
 * (ostatnie 4 znaki jawnej wartości) — nigdy ciphertext ani jawny klucz.
 */
public record LlmApiKeySummary(Long id, String provider, String label, String keyHint, Instant updatedAt) {

    public static LlmApiKeySummary from(LlmApiKey key) {
        return new LlmApiKeySummary(key.getId(), key.getProvider().name(), key.getLabel(),
                key.getKeyHint(), key.getUpdatedAt());
    }
}
