package pl.michalbzowski.windband.application.command.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.*;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberCommandService {

    private final MemberRepository memberRepository;
    private final InstrumentRepository instrumentRepository;
    private final BandRepository bandRepository;

    public Member createMember(CreateMemberCommand cmd) {
        Band band = bandRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Default band not found"));
        Member member = Member.create(
                cmd.getFirstName(),
                cmd.getLastName(),
                cmd.getDateOfBirth(),
                band
        );
        if (cmd.getJoinedDate() != null) {
            member.setJoinedDate(cmd.getJoinedDate());
        }
        if (cmd.getEmail() != null || cmd.getPhone() != null) {
            member.updateContact(cmd.getEmail(), cmd.getPhone());
        }
        member = memberRepository.save(member);

        if (cmd.getInstrumentId() != null) {
            Instrument instrument = instrumentRepository.findById(cmd.getInstrumentId())
                    .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + cmd.getInstrumentId()));
            member.addInstrument(instrument, true);
            member = memberRepository.save(member);
        }

        return member;
    }

    public Member updateMember(UpdateMemberCommand cmd) {
        Member member = memberRepository.findById(cmd.getMemberId())
                .orElseThrow(() -> new MemberNotFoundException(cmd.getMemberId()));
        member.update(cmd.getFirstName(), cmd.getLastName(), cmd.getDateOfBirth(), cmd.isActive());
        member.updateContact(cmd.getEmail(), cmd.getPhone());
        if (cmd.getJoinedDate() != null) {
            member.setJoinedDate(cmd.getJoinedDate());
        }
        if (cmd.getResignedDate() != null) {
            member.setResignedDate(cmd.getResignedDate());
        } else if (!cmd.isActive()) {
            member.setResignedDate(LocalDate.now());
        }
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
