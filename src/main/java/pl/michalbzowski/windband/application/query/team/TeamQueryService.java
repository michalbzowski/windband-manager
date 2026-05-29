package pl.michalbzowski.windband.application.query.team;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.user.AppUserRepository;

@Service
@RequiredArgsConstructor
public class TeamQueryService {

    private final AppUserRepository appUserRepository;
    private final BandRepository bandRepository;

    public boolean isUsernameAvailable(String username) {
        return username != null && username.length() >= 3
                && !appUserRepository.existsByUsername(username);
    }

    public boolean isEmailAvailable(String email) {
        return email != null && email.contains("@")
                && !appUserRepository.existsByEmail(email);
    }

    public boolean isSlugAvailable(String slug) {
        return slug != null && slug.length() >= 3
                && !bandRepository.existsBySlug(slug);
    }
}
