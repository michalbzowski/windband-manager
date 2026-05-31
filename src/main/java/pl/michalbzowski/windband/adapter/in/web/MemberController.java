package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.command.member.AssignInstrumentCommand;
import pl.michalbzowski.windband.application.command.member.ChangeInstrumentCommand;
import pl.michalbzowski.windband.application.command.member.CreateMemberCommand;
import pl.michalbzowski.windband.application.command.member.MemberCommandService;
import pl.michalbzowski.windband.application.command.member.UpdateMemberCommand;
import pl.michalbzowski.windband.application.dto.MemberDto;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;

import java.util.List;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberCommandService commandService;
    private final MemberQueryService queryService;

    @GetMapping
    public List<MemberDto> getAllMembers() {
        return queryService.getAllActiveMembers();
    }

    @GetMapping("/{id}")
    public MemberDto getMember(@PathVariable Long id) {
        return queryService.getMemberById(id);
    }

    @PostMapping
    public ResponseEntity<MemberDto> createMember(@RequestBody CreateMemberCommand cmd) {
        var member = commandService.createMember(cmd);
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

    @PutMapping("/{id}/instrument")
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
}
