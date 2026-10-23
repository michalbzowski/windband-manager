package pl.michalbzowski.windband.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguracja skarbca kluczy LLM: {@code app.llmkeys.master-key}
 * (env {@code LLM_KEYS_MASTER_KEY}, Base64, 32 bajty).
 *
 * <p>Pusty/blank wpis (np. {@code ${LLM_KEYS_MASTER_KEY:}} bez env) jest
 * normalizowany do {@code null} = skarbiec wyłączony (fail-closed przy użyciu).
 */
@ConfigurationProperties(prefix = "app.llmkeys")
public record LlmKeysConfig(String masterKey) {

    public LlmKeysConfig {
        masterKey = masterKey == null || masterKey.isBlank() ? null : masterKey.trim();
    }

    public boolean enabled() {
        return masterKey != null;
    }
}
