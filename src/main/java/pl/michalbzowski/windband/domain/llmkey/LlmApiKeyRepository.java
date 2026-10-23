package pl.michalbzowski.windband.domain.llmkey;

import java.util.List;
import java.util.Optional;

/**
 * Port repozytorium kluczy API użytkownika (skarbiec).
 * Implementacja: {@code adapter.out.persistence.llmkey.SpringDataLlmApiKeyRepository}.
 */
public interface LlmApiKeyRepository {

    LlmApiKey save(LlmApiKey key);

    List<LlmApiKey> findByOwnerId(Long ownerId);

    Optional<LlmApiKey> findByOwnerIdAndProvider(Long ownerId, LlmProvider provider);

    Optional<LlmApiKey> findByIdAndOwnerId(Long id, Long ownerId);

    void delete(LlmApiKey key);
}
