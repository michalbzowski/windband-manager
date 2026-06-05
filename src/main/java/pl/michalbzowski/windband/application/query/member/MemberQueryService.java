package pl.michalbzowski.windband.application.query.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.member.MemberNotFoundException;
import pl.michalbzowski.windband.application.dto.MemberDto;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberQueryService {

    private final MemberRepository memberRepository;
    private final InstrumentRepository instrumentRepository;

    public List<MemberDto> getAllActiveMembers() {
        return getAllActiveMembers(null);
    }

    public List<MemberDto> getAllActiveMembers(Long teamId) {
        // Build instrument priority map (lower number = higher priority)
        var instrumentPriorities = instrumentRepository.findAllOrderBySortPriority().stream()
                .collect(Collectors.toMap(
                        Instrument::getName,
                        Instrument::getSortPriority,
                        (existing, replacement) -> existing,
                        java.util.LinkedHashMap::new
                ));

        List<Member> members = (teamId != null)
                ? memberRepository.findAllActiveByBandId(teamId)
                : memberRepository.findAllActive();

        return members.stream()
                .sorted(Comparator
                        .<Member>comparingInt(m -> {
                            Integer priority = m.getPrimaryInstrument()
                                    .map(i -> instrumentPriorities.get(i.getName()))
                                    .orElse(Integer.MAX_VALUE);
                            return priority != null ? priority : Integer.MAX_VALUE;
                        })
                        .thenComparing(Member::getLastName)
                        .thenComparing(Member::getFirstName))
                .map(this::toDto)
                .toList();
    }

    public MemberDto getMemberById(Long id) {
        return memberRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new MemberNotFoundException(id));
    }

    public Member getMemberEntityById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new MemberNotFoundException(id));
    }

    public long getActiveMemberCount() {
        return getActiveMemberCount(null);
    }

    public long getActiveMemberCount(Long teamId) {
        if (teamId != null) {
            return memberRepository.findAllActiveByBandId(teamId).size();
        }
        return memberRepository.findAllActive().size();
    }

    public List<Member> findAllActiveMembers() {
        return findAllActiveMembers(null);
    }

    public List<Member> findAllActiveMembers(Long teamId) {
        if (teamId != null) {
            return memberRepository.findAllActiveByBandId(teamId);
        }
        return memberRepository.findAllActive();
    }

    public long getMinorCount() {
        return getMinorCount(null);
    }

    public long getMinorCount(Long teamId) {
        List<Member> members = (teamId != null)
                ? memberRepository.findAllActiveByBandId(teamId)
                : memberRepository.findAllActive();
        return members.stream()
                .filter(Member::isMinor)
                .count();
    }

    public long getSeniorCount() {
        return getSeniorCount(null);
    }

    public long getSeniorCount(Long teamId) {
        List<Member> members = (teamId != null)
                ? memberRepository.findAllActiveByBandId(teamId)
                : memberRepository.findAllActive();
        return members.stream()
                .filter(Member::isSenior)
                .count();
    }

    private MemberDto toDto(Member m) {
        return new MemberDto(
                m.getId(),
                m.getFirstName(),
                m.getLastName(),
                m.getDateOfBirth(),
                m.getAge(),
                m.isMinor(),
                m.isSenior(),
                m.getEmail(),
                m.getPhone(),
                m.isActive(),
                m.getPrimaryInstrument().map(inst -> inst.getName()).orElse(null),
                m.getAllInstruments().stream().map(i -> i.getName()).toList(),
                m.getJoinedDate(),
                m.getResignedDate(),
                m.getPrimaryInstrument().map(inst -> inst.getId()).orElse(null),
                m.getPrimaryInstrument().map(inst -> inst.getName()).orElse(null)
        );
    }
}
