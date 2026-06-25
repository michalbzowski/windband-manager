package pl.michalbzowski.windband.application.query.systemadmin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.user.AppUser;
import pl.michalbzowski.windband.domain.user.AppUserRepository;

import java.util.List;

/**
 * Application query service for system admin operations.
 * Controllers must not depend on domain repositories directly (ArchUnit).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemAdminQueryService {

    private final AppUserRepository appUserRepository;

    public List<AppUser> findAllUsers() {
        return appUserRepository.findAll();
    }

    public List<AppUser> findSystemAdmins() {
        return appUserRepository.findBySystemAdminTrue();
    }
}
