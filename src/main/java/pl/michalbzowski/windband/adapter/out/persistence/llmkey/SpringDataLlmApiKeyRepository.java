package pl.michalbzowski.windband.adapter.out.persistence.llmkey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.michalbzowski.windband.domain.llmkey.LlmApiKey;
import pl.michalbzowski.windband.domain.llmkey.LlmApiKeyRepository;

/**
 * Spring Data adapter dla {@link LlmApiKeyRepository}; metody pochodne
 * (findByOwnerId, findByOwnerIdAndProvider, findByIdAndOwnerId) generuje Spring Data.
 */
@Repository
public interface SpringDataLlmApiKeyRepository extends JpaRepository<LlmApiKey, Long>, LlmApiKeyRepository {
}
