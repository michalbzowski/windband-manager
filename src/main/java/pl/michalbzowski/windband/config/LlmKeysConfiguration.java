package pl.michalbzowski.windband.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.michalbzowski.windband.adapter.out.crypto.AesGcmSecretCipher;
import pl.michalbzowski.windband.application.config.LlmKeysConfig;
import pl.michalbzowski.windband.application.security.SecretCipher;

/**
 * Wiąże port {@link SecretCipher} z implementacją AES-256-GCM zasilaną
 * z {@code app.llmkeys.master-key} (env {@code LLM_KEYS_MASTER_KEY}).
 */
@Configuration
public class LlmKeysConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecretCipher.class)
    public SecretCipher secretCipher(LlmKeysConfig cfg) {
        return new AesGcmSecretCipher(cfg.masterKey());
    }
}
