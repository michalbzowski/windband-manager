package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.in.security.ActiveTeamResolver;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.member.AssignInstrumentCommand;
import pl.michalbzowski.windband.application.command.member.ChangeInstrumentCommand;
import pl.michalbzowski.windband.application.command.member.CreateMemberCommand;
import pl.michalbzowski.windband.application.command.member.MemberCommandService;
import pl.michalbzowski.windband.application.command.member.MemberNotFoundException;
import pl.michalbzowski.windband.application.command.member.UpdateMemberCommand;
import pl.michalbzowski.windband.application.dto.MemberDto;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.application.security.CurrentUser;

import java.util.List;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private static final Logger log = LoggerFactory.getLogger(MemberController.class);

    private final MemberCommandService commandService;
    private final MemberQueryService queryService;
    private final ActiveTeamResolver activeTeamResolver;

    @GetMapping
    public List<MemberDto> getAllMembers(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session) {
        Long teamId = activeTeamResolver.resolve(oidcUser, session);
        return queryService.getAllActiveMembers(teamId);
    }

    @GetMapping("/{id}")
    public MemberDto getMember(@PathVariable Long id,
                               @AuthenticationPrincipal OidcUser oidcUser,
                               HttpSession session) {
        // Pentest P0 (A01/BOLA): surowy id bez sprawdzenia teamu pozwalał czytać II członków
        // dowolnego bandu. Teraz: tylko członkowie aktywnego zespołu żądającego.
        Long activeTeamId = requireActiveTeam(oidcUser, session);
        return queryService.requireMemberOfTeam(id, activeTeamId);
    }

    @PostMapping
    public ResponseEntity<MemberDto> createMember(@RequestBody CreateMemberCommand cmd,
                                                  @AuthenticationPrincipal OidcUser oidcUser,
                                                  HttpSession session) {
        Long activeTeamId = activeTeamResolver.resolve(oidcUser, session);
        var member = commandService.createMember(cmd, activeTeamId, oidcUser instanceof WindbandOidcUser ? (WindbandOidcUser) oidcUser : null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(queryService.getMemberById(member.getId()));
    }

    @PutMapping("/{id}")
    public MemberDto updateMember(@PathVariable Long id, @RequestBody UpdateMemberCommand cmd,
                                  @AuthenticationPrincipal OidcUser oidcUser,
                                  HttpSession session) {
        cmd.setMemberId(id);
        // Pentest P0 (A01/BOLA): edycja dozwolona wyłącznie dla członków aktywnego zespołu.
        verifyMemberInActiveTeam(id, oidcUser, session);
        var member = commandService.updateMember(cmd, oidcUser instanceof WindbandOidcUser ? (WindbandOidcUser) oidcUser : null);
        return queryService.getMemberById(member.getId());
    }

    @PostMapping("/{id}/instruments")
    public ResponseEntity<Void> assignInstrument(@PathVariable Long id,
                                                 @RequestBody AssignInstrumentCommand cmd,
                                                 @AuthenticationPrincipal OidcUser oidcUser,
                                                 HttpSession session) {
        cmd.setMemberId(id);
        verifyMemberInActiveTeam(id, oidcUser, session);
        commandService.assignInstrument(cmd);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/tag")
    public ResponseEntity<Void> changeInstrument(@PathVariable Long id,
                                                 @RequestBody ChangeInstrumentCommand cmd,
                                                 @AuthenticationPrincipal OidcUser oidcUser,
                                                 HttpSession session) {
        cmd.setMemberId(id);
        verifyMemberInActiveTeam(id, oidcUser, session);
        commandService.changeInstrument(cmd);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateMember(@PathVariable Long id,
                                                 @AuthenticationPrincipal OidcUser oidcUser,
                                                 HttpSession session) {
        verifyMemberInActiveTeam(id, oidcUser, session);
        commandService.deactivateMember(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/resend-welcome")
        public ResponseEntity<Void> resendWelcomeEmail(@PathVariable Long id,
                                                       @AuthenticationPrincipal OidcUser oidcUser,
                                                       HttpSession session) {
            // Gate spoza bloku try: błędy dostępu (403/404) nie mogą zostać
            // przełknięte przez catch(Exception) i zwrócone jako 500.
            verifyMemberInActiveTeam(id, oidcUser, session);
            try {
                CurrentUser currentUser = (oidcUser instanceof WindbandOidcUser wu) ? wu : null;
                commandService.resendWelcomeEmail(id, currentUser);
                return ResponseEntity.ok().build();
            } catch (MemberNotFoundException ex) {
                return ResponseEntity.notFound().build();
            } catch (Exception ex) {
                log.error("Failed to resend welcome email for member {}", id, ex);
                return ResponseEntity.internalServerError().build();
            }
        }

    private Long requireActiveTeam(OidcUser oidcUser, HttpSession session) {
        Long activeTeamId = activeTeamResolver.resolve(oidcUser, session);
        if (activeTeamId == null) {
            // Brak tożsamości windband + aktywnego zespołu — nie ma kontekstu, do którego
            // można by bezpiecznie odnieść per-id żądanie. GlobalExceptionHandler → 403.
            throw new org.springframework.security.access.AccessDeniedException(
                    "Brak aktywnego zespołu w bieżącej sesji");
        }
        return activeTeamId;
    }

    // Pentest P0 (A01/BOLA): wspólny gate dla mutujących per-id endpointów —
    // członk musi należeć do aktywnego zespołu żądającego, inaczej 403/404.
    private void verifyMemberInActiveTeam(Long memberId, OidcUser oidcUser, HttpSession session) {
        queryService.requireMemberOfTeam(memberId, requireActiveTeam(oidcUser, session));
    }
}
