package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;

import java.util.List;

/**
 * Global model attributes for teams — adds user's teams and active team info
 * to all templates. Respects session override (set by switch-team) for the
 * active team, so the nav reflects team switches even though the Principal
 * (WindbandOidcUser) was set at login time.
 *
 * Uses TeamQueryService (which is @Transactional) for all DB access to avoid
 * LazyInitializationException from accessing lazy-loaded entity proxies.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class TeamModelAdvice {

    private final TeamQueryService teamQueryService;

    @ModelAttribute("userTeams")
    public List<?> userTeams(@AuthenticationPrincipal OidcUser oidcUser) {
        if (oidcUser instanceof WindbandOidcUser wu) {
            return teamQueryService.getUserTeams(wu.getUserId());
        }
        return List.of();
    }

    @ModelAttribute("activeTeamId")
    public Long activeTeamId(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session) {
        if (!(oidcUser instanceof WindbandOidcUser wu)) {
            return null;
        }
        Long sessionTeamId = (Long) session.getAttribute("activeTeamId");
        // Check against DB instead of cached WindbandOidcUser.teamIds — the
        // principal is frozen at login time and doesn't know about teams
        // created later in the same session.
        if (sessionTeamId != null) {
            boolean stillBelongs = teamQueryService.getUserTeam(wu.getUserId(), sessionTeamId).isPresent();
            if (stillBelongs) {
                return sessionTeamId;
            }
        }
        return wu.getActiveTeamId();
    }

    @ModelAttribute("activeTeamSlug")
    public String activeTeamSlug(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session) {
        TeamQueryService.UserTeamDto info = getActiveTeamInfo(oidcUser, session);
        return info != null ? info.slug() : null;
    }

    @ModelAttribute("activeTeamName")
    public String activeTeamName(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session) {
        TeamQueryService.UserTeamDto info = getActiveTeamInfo(oidcUser, session);
        return info != null ? info.name() : null;
    }

    @ModelAttribute("activeTeamRole")
    public String activeTeamRole(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session) {
        TeamQueryService.UserTeamDto info = getActiveTeamInfo(oidcUser, session);
        return info != null ? info.role() : null;
    }

    private TeamQueryService.UserTeamDto getActiveTeamInfo(OidcUser oidcUser, HttpSession session) {
        if (!(oidcUser instanceof WindbandOidcUser wu)) {
            return null;
        }
        Long sessionTeamId = (Long) session.getAttribute("activeTeamId");
        // Check against DB instead of cached WindbandOidcUser.teamIds
        if (sessionTeamId != null) {
            var team = teamQueryService.getUserTeam(wu.getUserId(), sessionTeamId);
            if (team.isPresent()) {
                return team.get();
            }
        }
        Long teamId = wu.getActiveTeamId();
        if (teamId == null) return null;
        return teamQueryService.getUserTeam(wu.getUserId(), teamId).orElse(null);
    }
}