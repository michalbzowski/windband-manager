package pl.michalbzowski.windband.application.command.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.member.*;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberCommandService {

    private final MemberRepository memberRepository;
    private final InstrumentRepository instrumentRepository;

    public Member createMember(CreateMemberCommand cmd) {
        MemberRole role = MemberRole.valueOf(cmd.getRole().toUpperCase());
        Member member = Member.create(
                cmd.getFirstName(),
                cmd.getLastName(),
                cmd.getDateOfBirth(),
                role,
                cmd.isOspMember()
        );
        if (cmd.getEmail() != null || cmd.getPhone() != null) {
            member.updateContact(cmd.getEmail(), cmd.getPhone());
        }
        return memberRepository.save(member);
    }

    public Member updateMember(UpdateMemberCommand cmd) {
        Member member = memberRepository.findById(cmd.getMemberId())
                .orElseThrow(() -> new MemberNotFoundException(cmd.getMemberId()));
        member.updateContact(cmd.getEmail(), cmd.getPhone());
        return memberRepository.save(member);
    }

    public void assignInstrument(AssignInstrumentCommand cmd) {
        Member member = memberRepository.findById(cmd.getMemberId())
                .orElseThrow(() -> new MemberNotFoundException(cmd.getMemberId()));

        Instrument instrument = instrumentRepository.findByName(cmd.getInstrumentName())
                .orElseGet(() -> {
                    Instrument newInst = Instrument.create(cmd.getInstrumentName());
                    return instrumentRepository.save(newInst);
                });

        member.addInstrument(instrument, cmd.isPrimary());
        memberRepository.save(member);
    }

    public void deactivateMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        member.deactivate();
        memberRepository.save(member);
    }
}
