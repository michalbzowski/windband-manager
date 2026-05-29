package pl.michalbzowski.windband.application.command.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Testcontainers
@Transactional
class MemberCommandServiceTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("windband_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

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

        Member member = commandService.createMember(cmd);

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

        Member member = commandService.createMember(cmd);
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
        Member member = commandService.createMember(createCmd);

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
