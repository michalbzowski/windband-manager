package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.rehearsal.InviteGroupCommand;
import pl.michalbzowski.windband.application.command.rehearsal.InviteMemberCommand;
import pl.michalbzowski.windband.application.command.rehearsal.RecordAttendanceCommand;
import pl.michalbzowski.windband.application.command.rehearsal.RehearsalCommandService;
import pl.michalbzowski.windband.application.command.rehearsal.ScheduleRehearsalCommand;
import pl.michalbzowski.windband.application.query.rehearsal.RehearsalQueryService;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;

import java.util.List;

@RestController
@RequestMapping("/api/rehearsals")
@RequiredArgsConstructor
public class RehearsalController {

    private final RehearsalCommandService commandService;
    private final RehearsalQueryService queryService;
    private final TeamQueryService teamQueryService;

    @GetMapping
    public List<Rehearsal> getAllRehearsals(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session) {
        Long teamId = resolveActiveTeamId(oidcUser, session);
        return queryService.getAllRehearsals(teamId);
    }

    @GetMapping("/{id}")
    public Rehearsal getRehearsal(@PathVariable Long id) {
        return queryService.getRehearsalById(id);
    }

    @PostMapping
    public ResponseEntity<Rehearsal> scheduleRehearsal(@RequestBody ScheduleRehearsalCommand cmd,
                                                       @AuthenticationPrincipal OidcUser oidcUser,
                                                       HttpSession session) {
        Long activeTeamId = resolveActiveTeamId(oidcUser, session);
        var rehearsal = commandService.scheduleRehearsal(cmd, activeTeamId);
        return ResponseEntity.status(HttpStatus.CREATED).body(rehearsal);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Rehearsal> updateRehearsal(@PathVariable Long id,
                                                    @RequestBody ScheduleRehearsalCommand cmd) {
        var rehearsal = commandService.updateRehearsal(id, cmd);
        return ResponseEntity.ok(rehearsal);
    }

    @PostMapping("/{id}/attendance")
    public ResponseEntity<Void> recordAttendance(@PathVariable Long id,
                                                 @RequestBody RecordAttendanceCommand cmd) {
        cmd.setRehearsalId(id);
        commandService.recordAttendance(cmd);
        return ResponseEntity.ok().build();
    }

    /**
     * Invite a single member to a rehearsal. Mirrors the corresponding
     * event endpoint ({@code POST /api/events/{id}/invite}) so the UI can
     * reuse the same modal flow for both bounded contexts.
     */
    @PostMapping("/{id}/invite")
    public ResponseEntity<Void> inviteMember(@PathVariable Long id,
                                             @RequestBody InviteMemberCommand cmd) {
        cmd.setRehearsalId(id);
        commandService.inviteMember(cmd);
        return ResponseEntity.ok().build();
    }

    /**
     * Invite every member of a group to a rehearsal. Mirrors
     * {@code POST /api/events/{id}/invite-group}.
     */
    @PostMapping("/{id}/invite-group")
    public ResponseEntity<Void> inviteGroup(@PathVariable Long id,
                                            @RequestBody InviteGroupCommand cmd) {
        cmd.setRehearsalId(id);
        commandService.inviteGroup(cmd);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRehearsal(@PathVariable Long id) {
        commandService.deleteRehearsal(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Create an ad-hoc rehearsal for "now" (today, current time).
     * Redirects to the rehearsal detail page so the UI can immediately
     * invite members and take attendance.
     */
    @PostMapping("/adhoc")
    public ResponseEntity<Void> createAdHocRehearsal(@AuthenticationPrincipal OidcUser oidcUser,
                                                      HttpSession session) {
        Long activeTeamId = resolveActiveTeamId(oidcUser, session);
        var rehearsal = commandService.createAdHocRehearsal(activeTeamId);
        // Redirect to the detail page via Location header
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/rehearsals/" + rehearsal.getId())
                .build();
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
