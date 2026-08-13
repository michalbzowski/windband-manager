package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.band.Band;

import java.util.List;

public interface SpringDataMemberRepository extends JpaRepository<Member, Long> {

    List<Member> findByActiveTrue();

    List<Member> findByActiveTrueAndBandId(Long bandId);

    // Issue #96 & #108 - Inactive members support (renamed from Resigned for consistency with main branch)
    List<Member> findByActiveFalse();

    List<Member> findByActiveFalseAndBandId(Long bandId);

    long countByActiveFalse();

    long countByActiveFalseAndBandId(Long bandId);

    // FIXED: Use proper naming convention - ByBand_Id not ByBandId
    List<Member> findAllByBandOrderByLastNameAscFirstNameAsc(Band band);

    @Override
    <S extends Member> S saveAndFlush(S entity);
}
