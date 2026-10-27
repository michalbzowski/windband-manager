package pl.michalbzowski.windband.application.query.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.application.command.member.CreateMemberCommand;
import pl.michalbzowski.windband.application.command.member.MemberCommandService;
import pl.michalbzowski.windband.application.dto.MemberDto;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import pl.michalbzowski.windband.BaseIntegrationTest;

class MemberQueryServiceTest extends BaseIntegrationTest {

    @Autowired
    private MemberQueryService queryService;

    @Autowired
    private MemberCommandService commandService;

    @Test
    void shouldReturnAllActiveMembers() {
        createTestMember("Jan", "Kowalski", LocalDate.of(1990, 1, 15));
        createTestMember("Piotr", "Nowak", LocalDate.of(1985, 6, 20));

        List<MemberDto> members = queryService.getAllActiveMembers();

        assertThat(members).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldReturnMemberById() {
        var member = createTestMember("Anna", "Wiśniewska", LocalDate.of(1995, 3, 10));

        MemberDto dto = queryService.getMemberById(member.getId());

        assertThat(dto.firstName()).isEqualTo("Anna");
        assertThat(dto.lastName()).isEqualTo("Wiśniewska");
    }

    @Test
    void shouldCountMinors() {
        createTestMember("Dziecko", "Młode", LocalDate.now().minusYears(10));

        long minorCount = queryService.getMinorCount();

        assertThat(minorCount).isGreaterThanOrEqualTo(1);
    }

    // ============ Pentest P0 (A01/BOLA): requireMemberOfTeam ============

    @Test
    void requireMemberOfTeam_memberInRequestedTeam_returnsDto() {
        var member = createTestMember("Własny", "Członek", LocalDate.of(1999, 5, 5)); // band 1

        MemberDto dto = queryService.requireMemberOfTeam(member.getId(), 1L);

        assertThat(dto.id()).isEqualTo(member.getId());
        assertThat(dto.firstName()).isEqualTo("Własny");
    }

    @Test
    void requireMemberOfTeam_memberInOtherBand_throwsCrossTeamAccessDenied() {
        var member = createTestMember("Obcy", "Członek", LocalDate.of(1997, 7, 7)); // band 1

        assertThatThrownBy(() -> queryService.requireMemberOfTeam(member.getId(), 2L))
                .isInstanceOf(pl.michalbzowski.windband.application.common.CrossTeamAccessDeniedException.class)
                .hasMessageContaining("2");
    }

    @Test
    void requireMemberOfTeam_unknownMember_throwsNotFound() {
        assertThatThrownBy(() -> queryService.requireMemberOfTeam(999_999L, 1L))
                .isInstanceOf(pl.michalbzowski.windband.application.command.member.MemberNotFoundException.class);
    }

    private pl.michalbzowski.windband.domain.member.Member createTestMember(
            String firstName, String lastName, LocalDate dob) {
        CreateMemberCommand cmd = new CreateMemberCommand();
        cmd.setFirstName(firstName);
        cmd.setLastName(lastName);
        cmd.setDateOfBirth(dob);
        return commandService.createMember(cmd, 1L, null);
    }
}
