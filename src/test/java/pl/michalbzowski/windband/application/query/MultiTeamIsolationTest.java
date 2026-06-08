package pl.michalbzowski.windband.application.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.event.CreateEventCommand;
import pl.michalbzowski.windband.application.command.event.EventCommandService;
import pl.michalbzowski.windband.application.command.member.CreateMemberCommand;
import pl.michalbzowski.windband.application.command.member.MemberCommandService;
import pl.michalbzowski.windband.application.command.rehearsal.RehearsalCommandService;
import pl.michalbzowski.windband.application.command.rehearsal.ScheduleRehearsalCommand;
import pl.michalbzowski.windband.application.query.event.EventQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.application.query.rehearsal.RehearsalQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import pl.michalbzowski.windband.BaseIntegrationTest;

/**
 * Tests verifying multi-team isolation:
 * Entities created in one team (band) must NOT appear in queries for another team.
 */
@Transactional
class MultiTeamIsolationTest extends BaseIntegrationTest {

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private MemberCommandService memberCommandService;

    @Autowired
    private MemberQueryService memberQueryService;

    @Autowired
    private RehearsalCommandService rehearsalCommandService;

    @Autowired
    private RehearsalQueryService rehearsalQueryService;

    @Autowired
    private EventCommandService eventCommandService;

    @Autowired
    private EventQueryService eventQueryService;

    private Long band1Id;
    private Long band2Id;

    @BeforeEach
    void setUp() {
        // Band 1 already exists from Flyway seed (id=1)
        // Create Band 2 for multi-team testing
        Band band2 = bandRepository.save(Band.create("Drugi Zespół", "drugi-zespol"));
        band2Id = band2.getId();
        band1Id = 1L;
    }

    // --- MEMBER ISOLATION ---

    @Test
    void memberInBand1_shouldNotBeVisibleInBand2() {
        // Given: a member in band 1
        CreateMemberCommand cmd = new CreateMemberCommand();
        cmd.setFirstName("Anna");
        cmd.setLastName("Zespół1");
        cmd.setDateOfBirth(LocalDate.of(1990, 1, 1));
        memberCommandService.createMember(cmd, band1Id);

        // And: a member in band 2
        CreateMemberCommand cmd2 = new CreateMemberCommand();
        cmd2.setFirstName("Bartek");
        cmd2.setLastName("Zespół2");
        cmd2.setDateOfBirth(LocalDate.of(1990, 1, 1));
        memberCommandService.createMember(cmd2, band2Id);

        // When: query members of band 1
        var band1Members = memberQueryService.getAllActiveMembers(band1Id);

        // Then: only band 1's member is visible
        assertThat(band1Members)
                .hasSize(1)
                .allMatch(m -> m.lastName().equals("Zespół1"));

        // When: query members of band 2
        var band2Members = memberQueryService.getAllActiveMembers(band2Id);

        // Then: only band 2's member is visible
        assertThat(band2Members)
                .hasSize(1)
                .allMatch(m -> m.lastName().equals("Zespół2"));
    }

    @Test
    void memberCreatedWithNullTeamId_shouldThrowException() {
        // Given: a member creation command without a team
        CreateMemberCommand cmd = new CreateMemberCommand();
        cmd.setFirstName("Bez");
        cmd.setLastName("Zespołu");
        cmd.setDateOfBirth(LocalDate.of(1990, 1, 1));

        // When/Then: creating a member with null teamId throws an exception
        assertThatThrownBy(() -> memberCommandService.createMember(cmd, null))
                .isInstanceOf(Exception.class);
    }

    // --- REHEARSAL ISOLATION ---

    @Test
    void rehearsalInBand1_shouldNotBeVisibleInBand2() {
        // Given: a rehearsal in band 1
        ScheduleRehearsalCommand cmd = new ScheduleRehearsalCommand();
        cmd.setDate(LocalDate.of(2025, 6, 10));
        cmd.setStartTime(LocalTime.of(18, 0));
        cmd.setLocation("Sala prób A");
        rehearsalCommandService.scheduleRehearsal(cmd, band1Id);

        // And: a rehearsal in band 2
        ScheduleRehearsalCommand cmd2 = new ScheduleRehearsalCommand();
        cmd2.setDate(LocalDate.of(2025, 6, 10));
        cmd2.setStartTime(LocalTime.of(19, 0));
        cmd2.setLocation("Sala prób B");
        rehearsalCommandService.scheduleRehearsal(cmd2, band2Id);

        // When: query rehearsals of band 1
        var band1Rehearsals = rehearsalQueryService.getAllRehearsals(band1Id);

        // Then: only band 1's rehearsal is visible
        assertThat(band1Rehearsals)
                .hasSize(1)
                .allMatch(r -> r.getLocation().equals("Sala prób A"));

        // When: query rehearsals of band 2
        var band2Rehearsals = rehearsalQueryService.getAllRehearsals(band2Id);

        // Then: only band 2's rehearsal is visible
        assertThat(band2Rehearsals)
                .hasSize(1)
                .allMatch(r -> r.getLocation().equals("Sala prób B"));
    }

    @Test
    void rehearsalCountIsTeamScoped() {
        // Given: rehearsals in different bands
        for (int i = 0; i < 3; i++) {
            ScheduleRehearsalCommand cmd = new ScheduleRehearsalCommand();
            cmd.setDate(LocalDate.of(2025, 6, 10 + i));
            cmd.setStartTime(LocalTime.of(18, 0));
            cmd.setLocation("Band1 - Próba " + i);
            rehearsalCommandService.scheduleRehearsal(cmd, band1Id);
        }
        for (int i = 0; i < 2; i++) {
            ScheduleRehearsalCommand cmd = new ScheduleRehearsalCommand();
            cmd.setDate(LocalDate.of(2025, 6, 10 + i));
            cmd.setStartTime(LocalTime.of(18, 0));
            cmd.setLocation("Band2 - Próba " + i);
            rehearsalCommandService.scheduleRehearsal(cmd, band2Id);
        }

        // Then: counts are team-specific
        assertThat(rehearsalQueryService.getAllRehearsals(band1Id)).hasSize(3);
        assertThat(rehearsalQueryService.getAllRehearsals(band2Id)).hasSize(2);
    }

    // --- EVENT ISOLATION ---

    @Test
    void eventInBand1_shouldNotBeVisibleInBand2() {
        // Given: an event in band 1
        CreateEventCommand cmd = new CreateEventCommand();
        cmd.setName("Koncert zespołu 1");
        cmd.setDate(LocalDate.of(2025, 7, 1));
        cmd.setStartTime(LocalTime.of(20, 0));
        cmd.setLocation("Amfiteatr A");
        cmd.setEventType("CONCERT");
        cmd.setPaymentType("FREE");
        eventCommandService.createEvent(cmd, band1Id);

        // And: an event in band 2
        CreateEventCommand cmd2 = new CreateEventCommand();
        cmd2.setName("Koncert zespołu 2");
        cmd2.setDate(LocalDate.of(2025, 7, 1));
        cmd2.setStartTime(LocalTime.of(20, 0));
        cmd2.setLocation("Amfiteatr B");
        cmd2.setEventType("CONCERT");
        cmd2.setPaymentType("FREE");
        eventCommandService.createEvent(cmd2, band2Id);

        // When: query events of band 1
        var band1Events = eventQueryService.getAllEvents(band1Id);

        // Then: only band 1's event is visible
        assertThat(band1Events)
                .hasSize(1)
                .allMatch(e -> e.getName().equals("Koncert zespołu 1"));

        // When: query events of band 2
        var band2Events = eventQueryService.getAllEvents(band2Id);

        // Then: only band 2's event is visible
        assertThat(band2Events)
                .hasSize(1)
                .allMatch(e -> e.getName().equals("Koncert zespołu 2"));
    }

    @Test
    void eventCountBetweenIsTeamScoped() {
        // Given: events in different bands on the same date range
        createEvent("Event Band1-A", "2025-08-01", band1Id);
        createEvent("Event Band1-B", "2025-08-15", band1Id);
        createEvent("Event Band2-A", "2025-08-01", band2Id);

        LocalDate from = LocalDate.of(2025, 8, 1);
        LocalDate to = LocalDate.of(2025, 8, 31);

        // Then: counts are team-specific
        assertThat(eventQueryService.getEventCountBetween(from, to, band1Id)).isEqualTo(2);
        assertThat(eventQueryService.getEventCountBetween(from, to, band2Id)).isEqualTo(1);
    }

    // --- MIXED ISOLATION: different entity types, same teams ---

    @Test
    void mixedEntitiesAreTeamScoped() {
        // Given: multiple entity types in band 1
        CreateMemberCommand m1 = new CreateMemberCommand();
        m1.setFirstName("M1");
        m1.setLastName("Band1");
        m1.setDateOfBirth(LocalDate.of(1990, 1, 1));
        memberCommandService.createMember(m1, band1Id);

        ScheduleRehearsalCommand r1 = new ScheduleRehearsalCommand();
        r1.setDate(LocalDate.of(2025, 6, 1));
        r1.setStartTime(LocalTime.of(18, 0));
        rehearsalCommandService.scheduleRehearsal(r1, band1Id);

        // And: same entity types in band 2
        CreateMemberCommand m2 = new CreateMemberCommand();
        m2.setFirstName("M2");
        m2.setLastName("Band2");
        m2.setDateOfBirth(LocalDate.of(1990, 1, 1));
        memberCommandService.createMember(m2, band2Id);

        // Then: each team sees only its own data
        assertThat(memberQueryService.getAllActiveMembers(band1Id))
                .hasSize(1)
                .allMatch(m -> m.lastName().equals("Band1"));

        assertThat(memberQueryService.getAllActiveMembers(band2Id))
                .hasSize(1)
                .allMatch(m -> m.lastName().equals("Band2"));

        assertThat(rehearsalQueryService.getAllRehearsals(band1Id)).hasSize(1);
        assertThat(rehearsalQueryService.getAllRehearsals(band2Id)).isEmpty();
    }

    // --- Helpers ---

    private void createEvent(String name, String dateString, Long teamId) {
        CreateEventCommand cmd = new CreateEventCommand();
        cmd.setName(name);
        cmd.setDate(LocalDate.parse(dateString));
        cmd.setStartTime(LocalTime.of(20, 0));
        cmd.setLocation("Location");
        cmd.setEventType("CONCERT");
        cmd.setPaymentType("FREE");
        eventCommandService.createEvent(cmd, teamId);
    }
}
