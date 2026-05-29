package pl.michalbzowski.windband.adapter.in.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import pl.michalbzowski.windband.domain.user.AppUser;
import pl.michalbzowski.windband.domain.user.AppUserRepository;
import pl.michalbzowski.windband.domain.user.UserTeamRoleRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamAwareUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;
    private final UserTeamRoleRepository userTeamRoleRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        Long activeTeamId = user.getTeamRoles().stream()
                .filter(r -> r.isAdmin())
                .map(r -> r.getTeam().getId())
                .findFirst()
                .orElseGet(() -> user.getTeamRoles().stream()
                        .map(r -> r.getTeam().getId())
                        .findFirst()
                        .orElse(null));

        String activeTeamSlug = null;
        String activeTeamRole = null;

        if (activeTeamId != null) {
            var role = user.getTeamRoles().stream()
                    .filter(r -> r.getTeam().getId().equals(activeTeamId))
                    .findFirst();
            if (role.isPresent()) {
                activeTeamSlug = role.get().getTeam().getSlug();
                activeTeamRole = role.get().getRole().name();
            }
        }

        List<Long> teamIds = user.getTeamRoles().stream()
                .map(r -> r.getTeam().getId())
                .toList();

        return new TeamAwareUserDetails(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPasswordHash(),
                user.isActive(),
                activeTeamId,
                activeTeamSlug,
                activeTeamRole,
                teamIds
        );
    }
}
