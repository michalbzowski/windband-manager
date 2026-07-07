package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.member.InstrumentCommandService;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;
import pl.michalbzowski.windband.domain.member.Instrument;

import java.util.List;

@RestController
@RequestMapping("/api/instruments")
@RequiredArgsConstructor
public class InstrumentController {

    private final InstrumentCommandService instrumentCommandService;
    private final TeamQueryService teamQueryService;

    @GetMapping
    public List<Instrument> getAllInstruments(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session) {
        Long activeTeamId = resolveActiveTeamId(oidcUser, session);
        return instrumentCommandService.getAllInstruments(activeTeamId);
    }

    @PostMapping
    public ResponseEntity<Instrument> createInstrument(@RequestBody InstrumentRequest request,
                                                       @AuthenticationPrincipal OidcUser oidcUser,
                                                       HttpSession session) {
        Long activeTeamId = resolveActiveTeamId(oidcUser, session);
        var instrument = instrumentCommandService.createInstrument(request.name(), request.description(), request.sortPriority(), activeTeamId);
        return ResponseEntity.status(HttpStatus.CREATED).body(instrument);
    }

    @PutMapping("/{id}")
    public Instrument updateInstrument(@PathVariable Long id, @RequestBody InstrumentRequest request,
                                       @AuthenticationPrincipal OidcUser oidcUser,
                                       HttpSession session) {
        Long activeTeamId = resolveActiveTeamId(oidcUser, session);
        return instrumentCommandService.updateInstrument(id, request.name(), request.description(), request.sortPriority(), activeTeamId);
    }

    @PutMapping("/{id}/priority")
    public Instrument updateSortPriority(@PathVariable Long id, @RequestBody PriorityRequest request,
                                         @AuthenticationPrincipal OidcUser oidcUser,
                                         HttpSession session) {
        Long activeTeamId = resolveActiveTeamId(oidcUser, session);
        return instrumentCommandService.updateSortPriority(id, request.priority(), activeTeamId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInstrument(@PathVariable Long id,
                                                 @AuthenticationPrincipal OidcUser oidcUser,
                                                 HttpSession session) {
        Long activeTeamId = resolveActiveTeamId(oidcUser, session);
        instrumentCommandService.deleteInstrument(id, activeTeamId);
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

    public record InstrumentRequest(String name, String description, Integer sortPriority) {}
    public record PriorityRequest(Integer priority) {}
}
