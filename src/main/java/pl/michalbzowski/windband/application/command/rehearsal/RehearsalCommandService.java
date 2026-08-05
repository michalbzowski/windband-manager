package pl.michalbzowski.windband.application.command.rehearsal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.member.MemberNotFoundException;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.band.MemberAttributeValueRepository;
import pl.michalbzowski.windband.domain.member.Group;
import pl.michalbzowski.windband.domain.member.GroupRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;
import pl.michalbzowski.windband.domain.rehearsal.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class RehearsalCommandService {

    private final RehearsalRepository rehearsalRepository;
    private final MemberRepository memberRepository;
    private final GroupRepository groupRepository;
    private final BandRepository bandRepository;
    private final MemberAttributeValueRepository memberAttributeValueRepository;

    /**
     * Create a new rehearsal. Members are NO LONGER auto-invited — the
     * previous flow fired a {@code RehearsalNotificationService} email
     * blast to every active member; the user removed that behaviour, so
     * the rehearsal is now created empty and the admin is expected to
     * click "Zaproś uczestników" / "Zaproś grupę" on the detail page.
     */
    public Rehearsal scheduleRehearsal(ScheduleRehearsalCommand cmd, Long teamId) {
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
        return rehearsalRepository.save(rehearsal);
    }

    /**
     * Update a scheduled rehearsal. Like {@link #scheduleRehearsal}, this
     * does NOT trigger any notification emails — that flow was removed
     * together with {@code RehearsalNotificationService}.
     */
    public Rehearsal updateRehearsal(Long id, ScheduleRehearsalCommand cmd) {
        Rehearsal rehearsal = rehearsalRepository.findById(id)
                .orElseThrow(() -> new RehearsalNotFoundException(id));
        rehearsal.updateDate(cmd.getDate());
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

    /**
     * Invite a single member to a rehearsal. Creates an
     * {@code Attendance(NO_RESPONSE)} row so the member can later be
     * marked PRESENT/EXCUSED/UNEXCUSED on the detail page. Throws
     * {@code IllegalStateException} if the member is already invited —
     * same idempotency contract as
     * {@code EventCommandService.inviteMember}.
     */
    public void inviteMember(InviteMemberCommand cmd) {
        Rehearsal rehearsal = rehearsalRepository.findById(cmd.getRehearsalId())
                .orElseThrow(() -> new RehearsalNotFoundException(cmd.getRehearsalId()));
        Member member = memberRepository.findById(cmd.getMemberId())
                .orElseThrow(() -> new MemberNotFoundException(cmd.getMemberId()));
        rehearsal.inviteMember(member);
        rehearsalRepository.save(rehearsal);
    }

    /**
     * Invite every member of a group to a rehearsal. Mirrors
     * {@code EventCommandService.inviteGroup}: manual groups iterate over
     * {@code group.getMembers()}, dynamic (attribute-backed / member-field
     * backed) groups evaluate {@code group.resolveSource(valueRepository)}
     * over the band's active members (because dynamic groups have no
     * rows in {@code group_members}). Members already invited are
     * silently skipped.
     */
    public void inviteGroup(InviteGroupCommand cmd) {
        Rehearsal rehearsal = rehearsalRepository.findById(cmd.getRehearsalId())
                .orElseThrow(() -> new RehearsalNotFoundException(cmd.getRehearsalId()));
        Group group = groupRepository.findById(cmd.getGroupId())
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + cmd.getGroupId()));

        var alreadyInvitedMemberIds = rehearsal.getAttendances().stream()
                .map(a -> a.getMember().getId())
                .collect(Collectors.toSet());

        List<Member> groupMembers;
        if (group.isDynamic()) {
            var source = group.resolveSource(memberAttributeValueRepository);
            groupMembers = memberRepository.findAllActiveByBandId(group.getBand().getId()).stream()
                    .filter(source::memberMatches)
                    .collect(Collectors.toList());
        } else {
            groupMembers = group.getMembers().stream()
                    .map(gm -> gm.getMember())
                    .collect(Collectors.toList());
        }

        for (var member : groupMembers) {
            if (!alreadyInvitedMemberIds.contains(member.getId())) {
                rehearsal.inviteMember(member);
            }
        }
        rehearsalRepository.save(rehearsal);
    }

    public void deleteRehearsal(Long id) {
        Rehearsal rehearsal = rehearsalRepository.findById(id)
                .orElseThrow(() -> new RehearsalNotFoundException(id));
        rehearsalRepository.delete(rehearsal);
    }

    /**
     * Create an ad-hoc rehearsal for today with current time.
     * Immediately returns the created rehearsal so the UI can redirect to detail view.
     */
    public Rehearsal createAdHocRehearsal(Long teamId) {
        Band band = bandRepository.findById(teamId)
                .orElseThrow(() -> new IllegalStateException("Band not found for team ID: " + teamId));
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now().withSecond(0).withNano(0);
        Rehearsal rehearsal = Rehearsal.schedule(today, now, "", band);
        return rehearsalRepository.save(rehearsal);
    }
}
