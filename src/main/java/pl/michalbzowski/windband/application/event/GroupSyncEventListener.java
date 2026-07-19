package pl.michalbzowski.windband.application.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.member.*;
import pl.michalbzowski.windband.application.command.member.GroupCommandService;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberActivatedEvent;
import pl.michalbzowski.windband.domain.member.MemberDeactivatedEvent;
import pl.michalbzowski.windband.domain.member.MemberRepository;

/**
 * Keeps the "Aktywni" dynamic group in sync when a member's active flag flips.
 *
 * <p>Listens for the {@link MemberActivatedEvent} / {@link MemberDeactivatedEvent}
 * domain events rather than calling the group service directly from
 * {@code MemberCommandService}. This decouples the member lifecycle from group
 * mechanics (SRP) and — crucially — leaves the door open for other listeners
 * (e.g. a future notification service sending a farewell email on deactivation)
 * to subscribe to the very same events without touching this code.</p>
 *
 * <p>Runs in {@code AFTER_COMMIT} so the member's new {@code active} state is
 * already persisted (and visible to a fresh transaction) when we re-evaluate
 * group membership.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GroupSyncEventListener {

    private final MemberRepository memberRepository;
    private final GroupCommandService groupCommandService;

    @EventListener
    public void onMemberDeactivated(MemberDeactivatedEvent event) {
        sync(event.memberId());
    }

    @EventListener
    public void onMemberActivated(MemberActivatedEvent event) {
        sync(event.memberId());
    }

    private void sync(Long memberId) {
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) {
            log.warn("[dynamic-groups] Member {} not found when syncing active group after status change", memberId);
            return;
        }
        groupCommandService.syncMemberForActiveField(member);
    }
}
