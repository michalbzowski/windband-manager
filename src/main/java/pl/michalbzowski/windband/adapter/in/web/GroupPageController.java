package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.command.member.CreateGroupCommand;
import pl.michalbzowski.windband.application.command.member.GroupCommandService;
import pl.michalbzowski.windband.application.dto.GroupDetailDto.GroupMemberDto;
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

    @GetMapping
    public String listPage(@ModelAttribute("activeTeamId") Long activeTeamId, Model model) {
        model.addAttribute("groups", groupQueryService.getAllGroups(activeTeamId));
        return "groups/list";
    }

    @GetMapping("/list")
    public String listFragment(@ModelAttribute("activeTeamId") Long activeTeamId, Model model) {
        model.addAttribute("groups", groupQueryService.getAllGroups(activeTeamId));
        return "groups/list :: #groups-content";
    }

    @GetMapping("/new")
    public String newGroupForm() {
        return "groups/form";
    }

    @GetMapping("/{id}")
    public String groupDetail(@PathVariable Long id, @ModelAttribute("activeTeamId") Long activeTeamId, Model model,
                              jakarta.servlet.http.HttpServletRequest request) {
        var groupDetail = groupQueryService.getGroupDetailById(id);
        model.addAttribute("group", groupDetail);
        Set<Long> memberIdsInGroup = groupDetail.members().stream()
                .map(GroupMemberDto::memberId)
                .collect(Collectors.toSet());
        List<MemberDto> availableMembers = memberQueryService.getAllActiveMembers(activeTeamId).stream()
                .filter(m -> !memberIdsInGroup.contains(m.id()))
                .toList();
        model.addAttribute("members", availableMembers);
        boolean isHtmx = "true".equalsIgnoreCase(request.getHeader("HX-Request"));
        if (isHtmx) {
            return "groups/detail :: #groups-content";
        } else {
            return "groups/detail";
        }
    }

    @PostMapping
    public String createGroup(@ModelAttribute CreateGroupCommand cmd, @ModelAttribute("activeTeamId") Long activeTeamId) {
        Band band = activeTeamId != null ? bandQueryService.getBandById(activeTeamId) : null;
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
                               @ModelAttribute("activeTeamId") Long activeTeamId, Model model) {
        groupCommandService.removeMemberFromGroup(groupId, memberId);
        var groupDetail = groupQueryService.getGroupDetailById(groupId);
        model.addAttribute("group", groupDetail);
        Set<Long> memberIdsInGroup = groupDetail.members().stream()
                .map(GroupMemberDto::memberId)
                .collect(Collectors.toSet());
        List<MemberDto> availableMembers = memberQueryService.getAllActiveMembers(activeTeamId).stream()
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
