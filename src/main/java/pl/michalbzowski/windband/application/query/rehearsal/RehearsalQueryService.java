package pl.michalbzowski.windband.application.query.rehearsal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.rehearsal.RehearsalNotFoundException;
import pl.michalbzowski.windband.application.dto.InviteOptionsDto;
import pl.michalbzowski.windband.application.dto.MemberDto;
import pl.michalbzowski.windband.application.query.member.GroupQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.domain.member.GroupRepository;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;
import pl.michalbzowski.windband.domain.rehearsal.RehearsalRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RehearsalQueryService {

    private final RehearsalRepository rehearsalRepository;
    private final MemberQueryService memberQueryService;
    private final GroupQueryService groupQueryService;
    private final GroupRepository groupRepository;

    public Rehearsal getRehearsalById(Long id) {
        return rehearsalRepository.findById(id)
                .orElseThrow(() -> new RehearsalNotFoundException(id));
    }

    public List<Rehearsal> getAllRehearsals() {
        return getAllRehearsals(null);
    }

    public List<Rehearsal> getAllRehearsals(Long teamId) {
        if (teamId != null) {
            return rehearsalRepository.findAllOrderByDateDescByBandId(teamId);
        }
        return rehearsalRepository.findAllOrderByDateDesc();
    }

    public List<Rehearsal> getRehearsalsBetween(LocalDate from, LocalDate to) {
        return getRehearsalsBetween(from, to, null);
    }

    public List<Rehearsal> getRehearsalsBetween(LocalDate from, LocalDate to, Long teamId) {
        if (teamId != null) {
            return rehearsalRepository.findByDateBetweenAndBandId(from, to, teamId);
        }
        return rehearsalRepository.findByDateBetween(from, to);
    }

    /**
     * Upcoming (today or later) rehearsals, sorted nearest-first (ascending by date).
     */
    public List<Rehearsal> getUpcomingRehearsals(Long teamId) {
        LocalDate today = LocalDate.now();
        return loadSortedAsc(teamId).stream()
                .filter(r -> !r.getDate().isBefore(today))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Past (before today) rehearsals, sorted most-recent-first (descending by date),
     * rendered after the upcoming section.
     */
    public List<Rehearsal> getPastRehearsals(Long teamId) {
        LocalDate today = LocalDate.now();
        return loadSortedAsc(teamId).stream()
                .filter(r -> r.getDate().isBefore(today))
                .sorted(java.util.Comparator.comparing(Rehearsal::getDate).reversed())
                .collect(java.util.stream.Collectors.toList());
    }

    private List<Rehearsal> loadSortedAsc(Long teamId) {
        List<Rehearsal> all = (teamId != null)
                ? rehearsalRepository.findAllOrderByDateDescByBandId(teamId)
                : rehearsalRepository.findAllOrderByDateDesc();
        all.sort(java.util.Comparator.comparing(Rehearsal::getDate));
        return all;
    }

    public long getRehearsalCountBetween(LocalDate from, LocalDate to) {
        return getRehearsalCountBetween(from, to, null);
    }

    public long getRehearsalCountBetween(LocalDate from, LocalDate to, Long teamId) {
        return getRehearsalsBetween(from, to, teamId).size();
    }

    /**
     * Groups + available members for the unified invite modal of a given rehearsal.
     * Mirrors {@code EventQueryService.getInviteOptions}: each group carries its member id
     * list (empty for dynamic groups, which fall back to all active band members) and the
     * individual member list only contains members who are NOT already invited (no attendance row).
     */
    public InviteOptionsDto getInviteOptions(Long rehearsalId, Long bandId) {
        Rehearsal rehearsal = getRehearsalById(rehearsalId);
        long effectiveBandId = bandId != null ? bandId
                : (rehearsal.getBand() != null ? rehearsal.getBand().getId() : 0L);

        Set<Long> invitedMemberIds = rehearsal.getAttendances().stream()
                .map(a -> a.getMember().getId())
                .collect(Collectors.toSet());

        List<InviteOptionsDto.GroupOption> groupOptions = groupQueryService.getAllGroups(effectiveBandId).stream()
                .map(summary -> new InviteOptionsDto.GroupOption(
                        summary.id(), summary.name(), summary.memberCount(),
                        resolveMemberIds(groupRepository.findById(summary.id()).orElse(null), effectiveBandId)))
                .collect(Collectors.toList());

        List<InviteOptionsDto.MemberOption> memberOptions = memberQueryService.getAllActiveMembers(effectiveBandId).stream()
                .filter(m -> !invitedMemberIds.contains(m.id()))
                .map(m -> new InviteOptionsDto.MemberOption(
                        m.id(), m.firstName() + " " + m.lastName()))
                .collect(Collectors.toList());

        return new InviteOptionsDto(groupOptions, memberOptions);
    }

    /**
     * Resolves a group's member ids. Dynamic (attribute-backed) groups have no
     * explicit membership rows, so we fall back to the band's active members —
     * the same fallback {@code RehearsalCommandService.inviteGroup} applies at write time.
     */
    private List<Long> resolveMemberIds(pl.michalbzowski.windband.domain.member.Group group, long bandId) {
        if (group != null && !group.getMembers().isEmpty()) {
            return group.getMembers().stream()
                    .map(gm -> gm.getMember().getId())
                    .collect(Collectors.toList());
        }
        return memberQueryService.getAllActiveMembers(bandId).stream()
                .map(MemberDto::id)
                .collect(Collectors.toList());
    }
}
