package pl.michalbzowski.windband.application.command.member;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.application.security.CurrentUser;
import pl.michalbzowski.windband.application.service.MemberWelcomeService;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

@Transactional
class MemberCommandServiceTest extends BaseIntegrationTest {

    @Autowired
    private MemberCommandService commandService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @MockBean
    private MemberWelcomeService memberWelcomeService;

    @Test
    void shouldCreateMember() {
        CreateMemberCommand cmd = new CreateMemberCommand();
        cmd.setFirstName("Jan");
        cmd.setLastName("Kowalski");
        cmd.setDateOfBirth(LocalDate.of(1990, 1, 15));
        cmd.setEmail("jan@email.pl");
        cmd.setPhone("123456789");

        Member member = commandService.createMember(cmd, 1L, null);

        assertThat(member.getId()).isNotNull();
        assertThat(member.getFirstName()).isEqualTo("Jan");
        assertThat(member.getEmail()).isEqualTo("jan@email.pl");
    }

    @Test
    void shouldSendWelcomeEmailWhenCreatingMemberWithEmail() {
        // given
        CreateMemberCommand cmd = new CreateMemberCommand();
        cmd.setFirstName("Anna");
        cmd.setLastName("Nowak");
        cmd.setDateOfBirth(LocalDate.of(1992, 3, 3));
        cmd.setEmail("anna@example.com");

        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.getName()).thenReturn("admin");

        // when
        commandService.createMember(cmd, 1L, currentUser);

        // then
        verify(memberWelcomeService).sendWelcomeIfNeeded(any(), any(), eq(currentUser));
    }

    @Test
    void shouldNotSendWelcomeEmailWhenCreatingMemberWithoutEmail() {
        // given
        CreateMemberCommand cmd = new CreateMemberCommand();
        cmd.setFirstName("Anna");
        cmd.setLastName("Nowak");
        cmd.setDateOfBirth(LocalDate.of(1992, 3, 3));
        // no email - member will have null email

        CurrentUser currentUser = mock(CurrentUser.class);

        // when
        commandService.createMember(cmd, 1L, currentUser);

        // then - mock bean intercepts the call, verify it was invoked
        verify(memberWelcomeService).sendWelcomeIfNeeded(any(), any(), any());
    }

    @Test
    void shouldSendWelcomeEmailWhenUpdatingMemberEmail() {
        // given: create member without email
        CreateMemberCommand createCmd = new CreateMemberCommand();
        createCmd.setFirstName("Piotr");
        createCmd.setLastName("Kowalski");
        createCmd.setDateOfBirth(LocalDate.of(1990, 6, 20));
        // no email
        Member member = commandService.createMember(createCmd, 1L, null);

        // when: update with email
        UpdateMemberCommand updateCmd = new UpdateMemberCommand();
        updateCmd.setMemberId(member.getId());
        updateCmd.setFirstName("Piotr");
        updateCmd.setLastName("Kowalski");
        updateCmd.setDateOfBirth(LocalDate.of(1990, 6, 20));
        updateCmd.setEmail("piotr@example.com");
        updateCmd.setActive(true);

        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.getName()).thenReturn("admin");

        commandService.updateMember(updateCmd, currentUser);

        // then
        verify(memberWelcomeService).sendWelcomeIfNeeded(any(), any(), eq(currentUser));
    }

    // existing tests below...
    @Test
    void shouldDeactivateMember() {
        CreateMemberCommand cmd = new CreateMemberCommand();
        cmd.setFirstName("Piotr");
        cmd.setLastName("Nowak");
        cmd.setDateOfBirth(LocalDate.of(1985, 6, 20));

        Member member = commandService.createMember(cmd, 1L, null);
        commandService.deactivateMember(member.getId());

        Member deactivated = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(deactivated.isActive()).isFalse();
    }

    @Test
    void shouldUpdateMemberInstrument() {
        // Given - tworzymy dwa instrumenty i muzyka z pierwszym
        Instrument trumpet = instrumentRepository.save(Instrument.create("Trabka"));
        Instrument clarinet = instrumentRepository.save(Instrument.create("Klarnet"));

        CreateMemberCommand createCmd = new CreateMemberCommand();
        createCmd.setFirstName("Jan");
        createCmd.setLastName("Kowalski");
        createCmd.setDateOfBirth(LocalDate.of(1990, 5, 15));
        createCmd.setInstrumentId(trumpet.getId());
        Member member = commandService.createMember(createCmd, 1L, null);

        assertThat(member.getPrimaryInstrument()).isPresent();
        assertThat(member.getPrimaryInstrument().get().getName()).isEqualTo("Trabka");

        // When - aktualizujemy muzyka z nowym instrumentem
        UpdateMemberCommand updateCmd = new UpdateMemberCommand();
        updateCmd.setMemberId(member.getId());
        updateCmd.setFirstName("Jan");
        updateCmd.setLastName("Kowalski");
        updateCmd.setDateOfBirth(LocalDate.of(1990, 5, 15));
        updateCmd.setActive(true);
        updateCmd.setInstrumentId(clarinet.getId());
        commandService.updateMember(updateCmd, null);

        // Then - muzyk powinien miec nowy instrument
        Member updated = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(updated.getPrimaryInstrument()).isPresent();
        assertThat(updated.getPrimaryInstrument().get().getName()).isEqualTo("Klarnet");
    }
}