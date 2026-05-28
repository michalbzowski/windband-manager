package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.command.member.CreateGroupCommand;
import pl.michalbzowski.windband.application.command.member.GroupCommandService;
import pl.michalbzowski.windband.application.query.member.GroupQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.domain.member.Group;

@Controller
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupPageController {

    private final GroupQueryService groupQueryService;
    private final GroupCommandService groupCommandService;
    private final MemberQueryService memberQueryService;

    @GetMapping
    public String listPage(Model model) {
        model.addAttribute("groups", groupQueryService.getAllGroups());
        return "groups/list";
    }

    @GetMapping("/list")
    public String listFragment(Model model) {
        model.addAttribute("groups", groupQueryService.getAllGroups());
        return "groups/list :: #groups-content";
    }

    @GetMapping("/new")
    public String newGroupForm() {
        return "groups/form";
    }

    @GetMapping("/{id}")
    public String groupDetail(@PathVariable Long id, Model model) {
        model.addAttribute("group", groupQueryService.getGroupDetailById(id));
        model.addAttribute("members", memberQueryService.getAllActiveMembers());
        return "groups/detail";
    }

    @PostMapping
    public String createGroup(@ModelAttribute CreateGroupCommand cmd) {
        Group group = groupCommandService.createGroup(cmd);
        return "redirect:/groups/" + group.getId();
    }

    @PostMapping("/{groupId}/members/{memberId}")
    public String addMember(@PathVariable Long groupId, @PathVariable Long memberId) {
        groupCommandService.addMemberToGroup(groupId, memberId);
        return "redirect:/groups/" + groupId;
    }

    @PostMapping("/{groupId}/members/{memberId}/remove")
    public String removeMember(@PathVariable Long groupId, @PathVariable Long memberId, Model model) {
        groupCommandService.removeMemberFromGroup(groupId, memberId);
        // Render full page instead of redirect so HTMX has CSS
        model.addAttribute("group", groupQueryService.getGroupDetailById(groupId));
        model.addAttribute("members", memberQueryService.getAllActiveMembers());
        return "groups/detail";
    }

    @PostMapping("/{id}/delete")
    public String deleteGroup(@PathVariable Long id) {
        groupCommandService.deleteGroup(id);
        return "redirect:/groups";
    }
}
