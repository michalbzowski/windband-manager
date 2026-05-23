package pl.michalbzowski.windband.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.IntegrationTest;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;
import pl.michalbzowski.windband.domain.member.MemberRole;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class MemberRepositoryAdapterIT extends IntegrationTest {

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void shouldSaveAndFindMember() {
        Member member = Member.create("Jan", "Kowalski",
                LocalDate.of(1990, 1, 15), MemberRole.MEMBER, true);

        Member saved = memberRepository.save(member);
        Member found = memberRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getFirstName()).isEqualTo("Jan");
        assertThat(found.getLastName()).isEqualTo("Kowalski");
        assertThat(found.isActive()).isTrue();
    }

    @Test
    void shouldFindAllActiveMembers() {
        memberRepository.save(Member.create("Jan", "Kowalski",
                LocalDate.of(1990, 1, 15), MemberRole.MEMBER, true));
        memberRepository.save(Member.create("Piotr", "Nowak",
                LocalDate.of(1985, 6, 20), MemberRole.MEMBER, false));

        List<Member> active = memberRepository.findAllActive();

        assertThat(active).hasSize(2);
    }

    @Test
    void shouldFindByRole() {
        memberRepository.save(Member.create("Jan", "Kowalski",
                LocalDate.of(1990, 1, 15), MemberRole.MEMBER, true));
        memberRepository.save(Member.create("Gość", "Testowy",
                LocalDate.of(1985, 6, 20), MemberRole.GUEST, false));

        List<Member> guests = memberRepository.findByRole(MemberRole.GUEST);

        assertThat(guests).hasSize(1);
        assertThat(guests.get(0).getFirstName()).isEqualTo("Gość");
    }
}
