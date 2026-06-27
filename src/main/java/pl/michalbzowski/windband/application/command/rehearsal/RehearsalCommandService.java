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
    private final RehearsalNotificationService notificationService;

    public Rehearsal scheduleRehearsal(ScheduleRehearsalCommand cmd, Long teamId) {
        System.out.println("[DEBUG] scheduleRehearsal teamId=" + teamId + " date=" + cmd.getDate() + " startTime=" + cmd.getStartTime() + " location=" + cmd.getLocation());
        Band band = bandRepository.findById(teamId)
                .orElseThrow(() -> new IllegalStateException("Band not found for team ID: " + teamId));
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
        Rehearsal saved = rehearsalRepository.save(rehearsal);
        System.out.println("[DEBUG] saved rehearsal id=" + saved.getId() + " location=" + saved.getLocation());
        notificationService.notifyMembersAboutNewRehearsal(saved);
        return saved;
    }

    public Rehearsal updateRehearsal(Long id, ScheduleRehearsalCommand cmd) {
        Rehearsal rehearsal = rehearsalRepository.findById(id)
                .orElseThrow(() -> new RehearsalNotFoundException(id));
        rehearsal.updateTime(cmd.getStartTime(), cmd.getEndTime());
        rehearsal.updateLocation(cmd.getLocation());
        rehearsal.updateNotes(cmd.getNotes());
        Rehearsal saved = rehearsalRepository.save(rehearsal);
        notificationService.notifyMembersAboutUpdatedRehearsal(saved);
        return saved;
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
