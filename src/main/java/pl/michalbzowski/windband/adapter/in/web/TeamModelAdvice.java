package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;
import pl.michalbzowski.windband.domain.user.UserTeamRoleRepository;

import java.util.List;

/**
 * Global model attributes for teams — adds user's teams and active team info
 * to all templates. Respects session override (set by switch-team) for the
 * active team, so the nav reflects team switches even though the Principal
 * (WindbandOidcUser) was set at login time.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class TeamModelAdvice {

    private final TeamQueryService teamQueryService;
    private final UserTeamRoleRepository userTeamRoleRepository;

    @ModelAttribute("userTeams")
    public List<?> userTeams(@AuthenticationPrincipal OidcUser oidcUser) {
        if (oidcUser instanceof WindbandOidcUser wu) {
            return teamQueryService.getUserTeams(wu.getUserId());
        }
        return List.of();
    }

    /**
 * Active team ID — checks session override first, falls back to Principal.
 */
    @ModelAttribute("activeTeamId")
    public Long activeTeamId(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session) {
        if (!(oidcUser instanceof WindbandOidcUser wu)) {
            return null;
        }
        Long sessionTeamId = (Long) session.getAttribute("activeTeamId");
        return sessionTeamId != null && wu.belongsToTeam(sessionTeamId)
                ? sessionTeamId : wu.getActiveTeamId();
    }

    /**
     * Active team slug — checks session override first, falls back to Principal.
     */
    @ModelAttribute("activeTeamSlug")
    public String activeTeamSlug(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session) {
        return getActiveTeamField(oidcUser, session, "slug");
    }

    /**
     * Active team role — checks session override first, falls back to Principal.
     */
    @ModelAttribute("activeTeamRole")
    public String activeTeamRole(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session) {
        return getActiveTeamField(oidcUser, session, "role");
    }

    private String getActiveTeamField(OidcUser oidcUser, HttpSession session, String field) {
        if (!(oidcUser instanceof WindbandOidcUser wu)) {
            return null;
        }
        // Check session override
        Long sessionTeamId = (Long) session.getAttribute("activeTeamId");
        Long teamId = sessionTeamId != null && wu.belongsToTeam(sessionTeamId)
                ? sessionTeamId : wu.getActiveTeamId();

        if (teamId == null) return null;

        var role = userTeamRoleRepository.findByUserIdAndTeamId(wu.getUserId(), teamId);
        if (role.isEmpty()) return null;

        return "slug".equals(field) ? role.get().getTeam().getSlug() : role.get().getRole().name();
    }
}