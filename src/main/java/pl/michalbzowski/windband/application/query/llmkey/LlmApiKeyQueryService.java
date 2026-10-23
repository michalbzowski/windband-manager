package pl.michalbzowski.windband.application.query.llmkey;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.michalbzowski.windband.application.dto.LlmApiKeySummary;
import pl.michalbzowski.windband.application.security.SecretCipher;
import pl.michalbzowski.windband.domain.llmkey.LlmApiKeyRepository;
import pl.michalbzowski.windband.domain.llmkey.LlmProvider;

/**
 * Odczyt kluczy API użytkownika: lista widoków (bez sekretów) oraz jawny klucz
 * dla wewnętrznych przepływów (np. start analizy partytury z kluczem OpenRouter
 * w nagłówku {@code X-LLM-Key}).
 */
@Service
public class LlmApiKeyQueryService {

    private final LlmApiKeyRepository repository;
    private final SecretCipher cipher;

    public LlmApiKeyQueryService(LlmApiKeyRepository repository, SecretCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    @Transactional(readOnly = true)
    public List<LlmApiKeySummary> list(Long ownerId) {
        if (ownerId == null) {
            return List.of();
        }
        return repository.findByOwnerId(ownerId).stream()
                .map(LlmApiKeySummary::from)
                .toList();
    }

    /**
     * Jawna wartość klucza użytkownika — WYŁĄCZNIE do przekazania in-memory
     * do zaufanego serwisu; nigdy nie logować i nigdy nie zwracać do UI.
     *
     * @return pusto, gdy użytkownik nie ma klucza tego dostawcy albo skarbiec
     *         jest wyłączony (analiza wystartuje wtedy bez klucza — provider
     *         serwera analizującego decyduje, czy ma czym pracować)
     * @throws IllegalStateException klucz istnieje, ale nie daje się odszyfrować
     *                               (rotacja/utrata klucza głównego)
     */
    @Transactional(readOnly = true)
    public Optional<String> plainKey(Long ownerId, LlmProvider provider) {
        if (ownerId == null || provider == null || !cipher.isEnabled()) {
            return Optional.empty();
        }
        return repository.findByOwnerIdAndProvider(ownerId, provider)
                .map(k -> cipher.decrypt(k.getCiphertext()));
    }
}
