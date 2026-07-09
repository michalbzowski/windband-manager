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

    private pl.michalbzowski.windband.domain.member.Member createTestMember(
            String firstName, String lastName, LocalDate dob) {
        CreateMemberCommand cmd = new CreateMemberCommand();
        cmd.setFirstName(firstName);
        cmd.setLastName(lastName);
        cmd.setDateOfBirth(dob);
        return commandService.createMember(cmd, 1L, null);
    }
}
