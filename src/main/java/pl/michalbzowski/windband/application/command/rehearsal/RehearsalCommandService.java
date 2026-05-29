package pl.michalbzowski.windband.application.command.rehearsal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.member.MemberNotFoundException;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;
import pl.michalbzowski.windband.domain.rehearsal.*;

@Service
@RequiredArgsConstructor
@Transactional
public class RehearsalCommandService {

    private final RehearsalRepository rehearsalRepository;
    private final MemberRepository memberRepository;
    private final BandRepository bandRepository;

    public Rehearsal scheduleRehearsal(ScheduleRehearsalCommand cmd) {
        Band band = bandRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Default band not found"));
        Rehearsal rehearsal = Rehearsal.schedule(
                cmd.getDate(),
                cmd.getStartTime(),
                cmd.getLocation(),
                band
        );
        if (cmd.getEndTime() != null) {
            rehearsal.updateTime(cmd.getStartTime(), cmd.getEndTime());
        }
        if (cmd.getNotes() != null) {
            rehearsal.updateNotes(cmd.getNotes());
        }
        return rehearsalRepository.save(rehearsal);
    }

    public Rehearsal updateRehearsal(Long id, ScheduleRehearsalCommand cmd) {
        Rehearsal rehearsal = rehearsalRepository.findById(id)
                .orElseThrow(() -> new RehearsalNotFoundException(id));
        rehearsal.updateTime(cmd.getStartTime(), cmd.getEndTime());
        rehearsal.updateLocation(cmd.getLocation());
        rehearsal.updateNotes(cmd.getNotes());
        return rehearsalRepository.save(rehearsal);
    }

    public void recordAttendance(RecordAttendanceCommand cmd) {
        Rehearsal rehearsal = rehearsalRepository.findById(cmd.getRehearsalId())
                .orElseThrow(() -> new RehearsalNotFoundException(cmd.getRehearsalId()));
        Member member = memberRepository.findById(cmd.getMemberId())
                .orElseThrow(() -> new MemberNotFoundException(cmd.getMemberId()));

        AttendanceStatus status = AttendanceStatus.valueOf(cmd.getStatus().toUpperCase());
        rehearsal.updateAttendance(member, status);
        rehearsalRepository.save(rehearsal);
    }

    public void deleteRehearsal(Long id) {
        Rehearsal rehearsal = rehearsalRepository.findById(id)
                .orElseThrow(() -> new RehearsalNotFoundException(id));
        rehearsalRepository.delete(rehearsal);
    }
}
