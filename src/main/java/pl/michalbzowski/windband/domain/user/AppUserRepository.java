package pl.michalbzowski.windband.domain.user;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository {

    AppUser save(AppUser user);

    Optional<AppUser> findById(Long id);

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
