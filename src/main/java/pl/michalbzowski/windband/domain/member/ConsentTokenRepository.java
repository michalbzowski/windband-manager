package pl.michalbzowski.windband.domain.member;

import java.util.Optional;
import java.util.UUID;

public interface ConsentTokenRepository {

    ConsentToken save(ConsentToken token);

    Optional<ConsentToken> findByToken(UUID token);

    void delete(ConsentToken token);
}