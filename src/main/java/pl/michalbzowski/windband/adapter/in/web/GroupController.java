package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpSession;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.member.CreateGroupCommand;
import pl.michalbzowski.windband.application.command.member.GroupCommandService;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.member.GroupQueryService;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;
import pl.michalbzowski.windband.domain.band.Band;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupCommandService groupCommandService;
    private final GroupQueryService groupQueryService;
    private final BandQueryService bandQueryService;
    private final TeamQueryService teamQueryService;

    @PostMapping
    public ResponseEntity<?> createGroup(@RequestBody CreateGroupRequest req,
                                         @AuthenticationPrincipal OidcUser oidcUser,
                                         HttpSession session) {
        Long activeTeamId = resolveActiveTeamId(oidcUser, session);
        Band band = activeTeamId != null ? bandQueryService.getBandById(activeTeamId) : null;
        var cmd = new CreateGroupCommand();
        cmd.setName(req.getName());
        cmd.setDescription(req.getDescription());
        var group = groupCommandService.createGroup(cmd, band);
        return ResponseEntity.ok(group);
    }

    @PostMapping("/{groupId}/members/{memberId}")
    public ResponseEntity<Void> addMember(@PathVariable Long groupId, @PathVariable Long memberId) {
        groupCommandService.addMemberToGroup(groupId, memberId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{groupId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long groupId, @PathVariable Long memberId) {
        groupCommandService.removeMemberFromGroup(groupId, memberId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        groupCommandService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }

    private Long resolveActiveTeamId(OidcUser oidcUser, HttpSession session) {
        if (oidcUser instanceof WindbandOidcUser wu) {
            Long sessionTeamId = (Long) session.getAttribute("activeTeamId");
            if (sessionTeamId != null && teamQueryService.getUserTeam(wu.getUserId(), sessionTeamId).isPresent()) {
                return sessionTeamId;
            }
            return wu.getActiveTeamId();
        }
        return null;
    }

    @Data
    public static class CreateGroupRequest {
        private String name;
        private String description;
    }
}
