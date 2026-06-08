package pl.michalbzowski.windband.application.command.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.BaseIntegrationTest;

@Transactional
class MemberCommandServiceTest extends BaseIntegrationTest {

    @Autowired
    private MemberCommandService commandService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Test
    void shouldCreateMember() {
        CreateMemberCommand cmd = new CreateMemberCommand();
        cmd.setFirstName("Jan");
        cmd.setLastName("Kowalski");
        cmd.setDateOfBirth(LocalDate.of(1990, 1, 15));
        cmd.setEmail("jan@email.pl");
        cmd.setPhone("123456789");

        Member member = commandService.createMember(cmd, 1L);

        assertThat(member.getId()).isNotNull();
        assertThat(member.getFirstName()).isEqualTo("Jan");
        assertThat(member.getEmail()).isEqualTo("jan@email.pl");
    }

    @Test
    void shouldDeactivateMember() {
        CreateMemberCommand cmd = new CreateMemberCommand();
        cmd.setFirstName("Piotr");
        cmd.setLastName("Nowak");
        cmd.setDateOfBirth(LocalDate.of(1985, 6, 20));

        Member member = commandService.createMember(cmd, 1L);
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
        Member member = commandService.createMember(createCmd, 1L);

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
        commandService.updateMember(updateCmd);

        // Then - muzyk powinien miec nowy instrument
        Member updated = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(updated.getPrimaryInstrument()).isPresent();
        assertThat(updated.getPrimaryInstrument().get().getName()).isEqualTo("Klarnet");
    }
}
