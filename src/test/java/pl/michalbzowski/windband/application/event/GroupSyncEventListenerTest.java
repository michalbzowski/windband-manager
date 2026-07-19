package pl.michalbzowski.windband.application.event;

import org.junit.jupiter.api.Test;
import pl.michalbzowski.windband.application.command.member.GroupCommandService;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberActivatedEvent;
import pl.michalbzowski.windband.domain.member.MemberDeactivatedEvent;
import pl.michalbzowski.windband.domain.member.MemberFieldSource;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupSyncEventListenerTest {

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final GroupCommandService groupCommandService = mock(GroupCommandService.class);
    private final GroupSyncEventListener listener =
            new GroupSyncEventListener(memberRepository, groupCommandService);

    private Member member(Long id) {
        // Use a minimal member stub via the real entity; band is not needed for sync assertion.
        Member m = Member.create("A", "B", LocalDate.now().minusYears(20),
                pl.michalbzowski.windband.domain.band.Band.create("B", "b"));
        return m;
    }

    @Test
    void onMemberDeactivated_syncsActiveGroupForMember() {
        Member m = member(5L);
        when(memberRepository.findById(5L)).thenReturn(java.util.Optional.of(m));

        listener.onMemberDeactivated(new MemberDeactivatedEvent(5L, 1L));

        verify(groupCommandService).syncMemberForActiveField(m);
    }

    @Test
    void onMemberActivated_syncsActiveGroupForMember() {
        Member m = member(7L);
        when(memberRepository.findById(7L)).thenReturn(java.util.Optional.of(m));

        listener.onMemberActivated(new MemberActivatedEvent(7L, 1L));

        verify(groupCommandService).syncMemberForActiveField(m);
    }

    @Test
    void onMissingMember_doesNotSync() {
        when(memberRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        listener.onMemberDeactivated(new MemberDeactivatedEvent(99L, 1L));

        verify(groupCommandService, org.mockito.Mockito.never()).syncMemberForActiveField(any());
    }
}
