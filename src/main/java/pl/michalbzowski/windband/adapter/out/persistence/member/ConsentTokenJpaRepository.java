package pl.michalbzowski.windband.adapter.out.persistence.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.michalbzowski.windband.domain.member.ConsentToken;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentTokenJpaRepository extends JpaRepository<ConsentToken, UUID> {
    Optional<ConsentToken> findByToken(UUID token);
}