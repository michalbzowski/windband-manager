package pl.michalbzowski.windband.domain.member;

import pl.michalbzowski.windband.domain.band.Band;

import java.util.List;
import java.util.Optional;

public interface MemberRepository {

    Member save(Member member);

    Member saveAndFlush(Member member);

    Optional<Member> findById(Long id);

    List<Member> findAllActive();

    List<Member> findAllResigned();

    List<Member> findAllResignedByBandId(Long bandId);

    List<Member> findAllActiveByBandId(Long bandId);

    // FIXED: Use proper method name with Band object parameter
    List<Member> findAllByBandOrderByLastNameAscFirstNameAsc(Band band);

    List<Member> findByInstrument(Instrument instrument);

    boolean existsById(Long id);

    void delete(Member member);
}
