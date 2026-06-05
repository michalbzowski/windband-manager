package pl.michalbzowski.windband.adapter.out.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.michalbzowski.windband.domain.user.AppUser;
import pl.michalbzowski.windband.domain.user.AppUserRepository;

import java.util.Optional;

@Repository
public interface SpringDataAppUserRepository extends JpaRepository<AppUser, Long>, AppUserRepository {

    @Override
    Optional<AppUser> findByUsername(String username);

    @Override
    Optional<AppUser> findByEmail(String email);

    @Override
    boolean existsByUsername(String username);

    @Override
    boolean existsByEmail(String email);

    Optional<AppUser> findByExternalId(String externalId);
}
