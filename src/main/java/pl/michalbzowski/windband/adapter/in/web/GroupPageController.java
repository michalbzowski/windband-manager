package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.member.CreateGroupCommand;
import pl.michalbzowski.windband.application.command.member.GroupCommandService;
import pl.michalbzowski.windband.application.dto.GroupDetailDto.GroupMemberDto;
import pl.michalbzowski.windband.application.dto.GroupDetailDto;
import pl.michalbzowski.windband.application.dto.MemberDto;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.member.GroupQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.member.Group;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupPageController {

    private final GroupQueryService groupQueryService;
    private final GroupCommandService groupCommandService;
    private final MemberQueryService memberQueryService;
    private final BandQueryService bandQueryService;

    private Long getActiveTeamId(OidcUser oidcUser) {
        if (oidcUser instanceof WindbandOidcUser wu) {
            return wu.getActiveTeamId();
        }
        return null;
    }

    @GetMapping
    public String listPage(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        Long teamId = getActiveTeamId(oidcUser);
        model.addAttribute("groups", groupQueryService.getAllGroups(teamId));
        return "groups/list";
    }

    @GetMapping("/list")
    public String listFragment(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        Long teamId = getActiveTeamId(oidcUser);
        model.addAttribute("groups", groupQueryService.getAllGroups(teamId));
        return "groups/list :: #groups-content";
    }

    @GetMapping("/new")
    public String newGroupForm() {
        return "groups/form";
    }

    @GetMapping("/{id}")
    public String groupDetail(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser, Model model) {
        Long teamId = getActiveTeamId(oidcUser);
        var groupDetail = groupQueryService.getGroupDetailById(id);
        model.addAttribute("group", groupDetail);
        Set<Long> memberIdsInGroup = groupDetail.members().stream()
                .map(GroupMemberDto::memberId)
                .collect(Collectors.toSet());
        List<MemberDto> availableMembers = memberQueryService.getAllActiveMembers(teamId).stream()
                .filter(m -> !memberIdsInGroup.contains(m.id()))
                .toList();
        model.addAttribute("members", availableMembers);
        return "groups/detail";
    }

    @PostMapping
    public String createGroup(@ModelAttribute CreateGroupCommand cmd, @AuthenticationPrincipal OidcUser oidcUser) {
        Long teamId = getActiveTeamId(oidcUser);
        Band band = teamId != null ? bandQueryService.getBandById(teamId) : null;
        Group group = groupCommandService.createGroup(cmd, band);
        return "redirect:/groups/" + group.getId();
    }

    @PostMapping("/{groupId}/members/{memberId}")
    public String addMember(@PathVariable Long groupId, @PathVariable Long memberId) {
        groupCommandService.addMemberToGroup(groupId, memberId);
        return "redirect:/groups/" + groupId;
    }

    @PostMapping("/{groupId}/members/{memberId}/remove")
    public String removeMember(@PathVariable Long groupId, @PathVariable Long memberId,
                               @AuthenticationPrincipal OidcUser oidcUser, Model model) {
        Long teamId = getActiveTeamId(oidcUser);
        groupCommandService.removeMemberFromGroup(groupId, memberId);
        // Return the group detail fragment for HTMX, avoiding redirect issues
        var groupDetail = groupQueryService.getGroupDetailById(groupId);
        model.addAttribute("group", groupDetail);
        Set<Long> memberIdsInGroup = groupDetail.members().stream()
                .map(GroupMemberDto::memberId)
                .collect(Collectors.toSet());
        List<MemberDto> availableMembers = memberQueryService.getAllActiveMembers(teamId).stream()
                .filter(m -> !memberIdsInGroup.contains(m.id()))
                .toList();
        model.addAttribute("members", availableMembers);
        return "groups/detail :: #groups-content";
    }

    @PostMapping("/{id}/delete")
    public String deleteGroup(@PathVariable Long id) {
        groupCommandService.deleteGroup(id);
        return "redirect:/groups";
    }
}
