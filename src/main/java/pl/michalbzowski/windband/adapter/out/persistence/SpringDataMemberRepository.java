package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;

public interface SpringDataMemberRepository extends JpaRepository<Member, Long> {

    List<Member> findByActiveTrue();

    @Override
    <S extends Member> S saveAndFlush(S entity);
}
