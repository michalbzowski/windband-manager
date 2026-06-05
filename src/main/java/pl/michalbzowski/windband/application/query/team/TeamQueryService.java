package pl.michalbzowski.windband.application.query.team;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.user.AppUserRepository;
import pl.michalbzowski.windband.domain.user.UserTeamRoleRepository;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamQueryService {

    private final AppUserRepository appUserRepository;
    private final BandRepository bandRepository;
    private final UserTeamRoleRepository userTeamRoleRepository;

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

    /**
     * Get all teams a user belongs to, with their roles.
     */
    public List<UserTeamDto> getUserTeams(Long userId) {
        var teamRoles = userTeamRoleRepository.findByUserId(userId);
        return teamRoles.stream()
                .map(tr -> new UserTeamDto(
                        tr.getTeam().getId(),
                        tr.getTeam().getName(),
                        tr.getTeam().getSlug(),
                        tr.getRole().name()
                ))
                .sorted(Comparator.comparing(UserTeamDto::name))
                .collect(Collectors.toList());
    }

    /**
     * Get details about a specific team.
     */
    public Band getTeamById(Long teamId) {
        return bandRepository.findById(teamId).orElse(null);
    }

    public record UserTeamDto(Long id, String name, String slug, String role) {}
}