package pl.michalbzowski.windband.adapter.in.web;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.command.member.GroupCommandService;
import pl.michalbzowski.windband.application.query.member.GroupQueryService;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupCommandService groupCommandService;
    private final GroupQueryService groupQueryService;

    @PostMapping
    public ResponseEntity<?> createGroup(@RequestBody CreateGroupRequest req) {
        var cmd = new pl.michalbzowski.windband.application.command.member.CreateGroupCommand();
        cmd.setName(req.getName());
        cmd.setDescription(req.getDescription());
        var group = groupCommandService.createGroup(cmd);
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

    @Data
    public static class CreateGroupRequest {
        private String name;
        private String description;
    }
}
