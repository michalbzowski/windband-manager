package pl.michalbzowski.windband.application.command.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.security.CurrentUser;
import pl.michalbzowski.windband.application.service.MemberWelcomeService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.*;

import java.time.LocalDate;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberCommandService {

    private final MemberRepository memberRepository;
    private final InstrumentRepository instrumentRepository;
    private final BandRepository bandRepository;
    private final MemberWelcomeService memberWelcomeService;

    public Member createMember(CreateMemberCommand cmd, Long teamId, CurrentUser currentUser) {
        Band band = bandRepository.findById(teamId)
                .orElseThrow(() -> new IllegalStateException("Band not found: " + teamId));
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
            member.updateContact(cmd.getEmail(), cmd.getPhone(), cmd.isEmailConsentGiven());
        }
        member = memberRepository.save(member);

        if (cmd.getInstrumentId() != null) {
            Instrument instrument = instrumentRepository.findByIdAndBandId(cmd.getInstrumentId(), band.getId())
                    .orElseGet(() -> instrumentRepository.findById(cmd.getInstrumentId())
                            .filter(i -> i.getBand() == null)
                            .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + cmd.getInstrumentId())));
            member.addInstrument(instrument, true);
            member = memberRepository.save(member);
        }

        // Send welcome email if needed
        memberWelcomeService.sendWelcomeIfNeeded(member, currentUser);

        return member;
    }

    public Member updateMember(UpdateMemberCommand cmd, CurrentUser currentUser) {
        Member member = memberRepository.findById(cmd.getMemberId())
                .orElseThrow(() -> new MemberNotFoundException(cmd.getMemberId()));
        member.update(cmd.getFirstName(), cmd.getLastName(), cmd.getDateOfBirth(), cmd.isActive());
        member.updateContact(cmd.getEmail(), cmd.getPhone(), cmd.isEmailConsentGiven());
        if (cmd.getJoinedDate() != null) {
            member.setJoinedDate(cmd.getJoinedDate());
        }
        if (cmd.getResignedDate() != null) {
            member.setResignedDate(cmd.getResignedDate());
        } else if (!cmd.isActive()) {
            member.setResignedDate(LocalDate.now());
        } else {
            member.setResignedDate(null);
        }
        if (cmd.getInstrumentId() != null) {
            Instrument instrument = instrumentRepository.findByIdAndBandId(cmd.getInstrumentId(), member.getBand().getId())
                    .orElseGet(() -> instrumentRepository.findById(cmd.getInstrumentId())
                            .filter(i -> i.getBand() == null)
                            .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + cmd.getInstrumentId())));
            member.changeInstrument(instrument);
        }
        member = memberRepository.saveAndFlush(member);

        // Send welcome email if needed (only on update if email changed or first time)
        memberWelcomeService.sendWelcomeIfNeeded(member, currentUser);

        return member;
    }

    public void assignInstrument(AssignInstrumentCommand cmd) {
        Member member = memberRepository.findById(cmd.getMemberId())
                .orElseThrow(() -> new MemberNotFoundException(cmd.getMemberId()));

        Instrument instrument = instrumentRepository.findByNameAndBandId(cmd.getInstrumentName(), member.getBand().getId())
                .orElseGet(() -> instrumentRepository.findByName(cmd.getInstrumentName())
                        .filter(i -> i.getBand() == null)
                        .orElseGet(() -> instrumentRepository.save(Instrument.create(cmd.getInstrumentName(), member.getBand()))));

        if (instrument.getBand() == null) {
            instrument.assignBand(member.getBand());
            instrument = instrumentRepository.save(instrument);
        }

        member.addInstrument(instrument, cmd.isPrimary());
        memberRepository.save(member);
    }

    public void changeInstrument(ChangeInstrumentCommand cmd) {
        Member member = memberRepository.findById(cmd.getMemberId())
                .orElseThrow(() -> new MemberNotFoundException(cmd.getMemberId()));

        Instrument instrument = instrumentRepository.findByIdAndBandId(cmd.getInstrumentId(), member.getBand().getId())
                .orElseGet(() -> instrumentRepository.findById(cmd.getInstrumentId())
                        .filter(i -> i.getBand() == null)
                        .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + cmd.getInstrumentId())));

        if (instrument.getBand() == null) {
            instrument.assignBand(member.getBand());
            instrument = instrumentRepository.save(instrument);
        }

        member.changeInstrument(instrument);
        memberRepository.save(member);
    }

    public void deactivateMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        member.deactivate();
        memberRepository.save(member);
    }
}