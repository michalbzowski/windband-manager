package pl.michalbzowski.windband.application.query.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.member.MemberNotFoundException;
import pl.michalbzowski.windband.application.dto.MemberDto;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberQueryService {

    private final MemberRepository memberRepository;

    public List<MemberDto> getAllActiveMembers() {
        return memberRepository.findAllActive().stream()
                .map(this::toDto)
                .toList();
    }

    public MemberDto getMemberById(Long id) {
        return memberRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new MemberNotFoundException(id));
    }

    public List<MemberDto> getMembersByRole(String role) {
        var memberRole = pl.michalbzowski.windband.domain.member.MemberRole.valueOf(role.toUpperCase());
        return memberRepository.findByRole(memberRole).stream()
                .map(this::toDto)
                .toList();
    }

    public long getActiveMemberCount() {
        return memberRepository.findAllActive().size();
    }

    public long getMinorCount() {
        return memberRepository.findAllActive().stream()
                .filter(Member::isMinor)
                .count();
    }

    public long getSeniorCount() {
        return memberRepository.findAllActive().stream()
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
                m.getRole().name(),
                m.isOspMember(),
                m.isActive(),
                m.getPrimaryInstrument().map(inst -> inst.getName()).orElse(null),
                m.getAllInstruments().stream().map(i -> i.getName()).toList(),
                m.getJoinedDate()
        );
    }
}
