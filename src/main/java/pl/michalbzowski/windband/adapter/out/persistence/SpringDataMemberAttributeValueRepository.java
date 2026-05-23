package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.band.MemberAttributeValue;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;
import java.util.Optional;

public interface SpringDataMemberAttributeValueRepository extends JpaRepository<MemberAttributeValue, Long> {

    Optional<MemberAttributeValue> findByMemberAndAttributeDef(Member member, MemberAttributeDef attributeDef);

    List<MemberAttributeValue> findByMember(Member member);

    void deleteByMember(Member member);
}
