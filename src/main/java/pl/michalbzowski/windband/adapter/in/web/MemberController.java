package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.member.AssignInstrumentCommand;
import pl.michalbzowski.windband.application.command.member.ChangeInstrumentCommand;
import pl.michalbzowski.windband.application.command.member.CreateMemberCommand;
import pl.michalbzowski.windband.application.command.member.MemberCommandService;
import pl.michalbzowski.windband.application.command.member.UpdateMemberCommand;
import pl.michalbzowski.windband.application.dto.MemberDto;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;

import java.util.List;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberCommandService commandService;
    private final MemberQueryService queryService;
    private final TeamQueryService teamQueryService;

    @GetMapping
    public List<MemberDto> getAllMembers(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session) {
        Long teamId = resolveActiveTeamId(oidcUser, session);
        return queryService.getAllActiveMembers(teamId);
    }

    @GetMapping("/{id}")
    public MemberDto getMember(@PathVariable Long id) {
        return queryService.getMemberById(id);
    }

    @PostMapping
    public ResponseEntity<MemberDto> createMember(@RequestBody CreateMemberCommand cmd,
                                                  @AuthenticationPrincipal OidcUser oidcUser,
                                                  HttpSession session) {
        Long activeTeamId = resolveActiveTeamId(oidcUser, session);
        var member = commandService.createMember(cmd, activeTeamId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(queryService.getMemberById(member.getId()));
    }

    @PutMapping("/{id}")
    public MemberDto updateMember(@PathVariable Long id, @RequestBody UpdateMemberCommand cmd) {
        cmd.setMemberId(id);
        var member = commandService.updateMember(cmd);
        return queryService.getMemberById(member.getId());
    }

    @PostMapping("/{id}/instruments")
    public ResponseEntity<Void> assignInstrument(@PathVariable Long id,
                                                  @RequestBody AssignInstrumentCommand cmd) {
        cmd.setMemberId(id);
        commandService.assignInstrument(cmd);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/tag")
    public ResponseEntity<Void> changeInstrument(@PathVariable Long id,
                                                   @RequestBody ChangeInstrumentCommand cmd) {
        cmd.setMemberId(id);
        commandService.changeInstrument(cmd);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateMember(@PathVariable Long id) {
        commandService.deactivateMember(id);
        return ResponseEntity.noContent().build();
    }

    private Long resolveActiveTeamId(OidcUser oidcUser, HttpSession session) {
        if (!(oidcUser instanceof WindbandOidcUser wu)) {
            return null;
        }
        Long sessionTeamId = (Long) session.getAttribute("activeTeamId");
        if (sessionTeamId != null) {
            boolean stillBelongs = teamQueryService.getUserTeam(wu.getUserId(), sessionTeamId).isPresent();
            if (stillBelongs) {
                return sessionTeamId;
            }
        }
        return wu.getActiveTeamId();
    }
}
