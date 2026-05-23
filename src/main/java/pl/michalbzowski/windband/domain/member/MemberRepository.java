package pl.michalbzowski.windband.domain.member;

import java.util.List;
import java.util.Optional;

public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findById(Long id);

    List<Member> findAllActive();

    List<Member> findByRole(MemberRole role);

    List<Member> findByInstrument(Instrument instrument);

    boolean existsById(Long id);

    void delete(Member member);
}
