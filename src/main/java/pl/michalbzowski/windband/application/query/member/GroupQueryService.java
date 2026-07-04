package pl.michalbzowski.windband.application.query.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.dto.GroupDetailDto;
import pl.michalbzowski.windband.application.dto.GroupDetailDto.GroupMemberDto;
import pl.michalbzowski.windband.application.dto.GroupSummaryDto;
import pl.michalbzowski.windband.domain.member.Group;
import pl.michalbzowski.windband.domain.member.GroupRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupQueryService {

    private final GroupRepository groupRepository;

    public List<GroupSummaryDto> getAllGroups() {
        return getAllGroups(null);
    }

    public List<GroupSummaryDto> getAllGroups(Long bandId) {
        List<Group> groups = (bandId != null)
                ? groupRepository.findAllWithMembersByBandId(bandId)
                : groupRepository.findAllWithMembers();
        return groups.stream()
                .map(g -> new GroupSummaryDto(
                        g.getId(),
                        g.getName(),
                        g.getDescription(),
                        g.getMemberCount(),
                        g.getDynamicSource() != null   // NEW: 5th arg
                ))
                .toList();
    }

    public GroupDetailDto getGroupDetailById(Long id) {
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + id));
        List<GroupMemberDto> memberDtos = group.getMembers().stream()
                .map(gm -> new GroupMemberDto(
                        gm.getId(),
                        gm.getMember().getId(),
                        gm.getMember().getFirstName() + " " + gm.getMember().getLastName(),
                        gm.getMember().getPrimaryInstrument().map(i -> i.getName()).orElse(null)
                ))
                .toList();
        return new GroupDetailDto(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getMemberCount(),
                memberDtos
        );
    }
}
