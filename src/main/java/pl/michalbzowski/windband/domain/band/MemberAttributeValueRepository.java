package pl.michalbzowski.windband.domain.band;

import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;
import java.util.Optional;

public interface MemberAttributeValueRepository {

    MemberAttributeValue save(MemberAttributeValue value);

    Optional<MemberAttributeValue> findById(Long id);

    Optional<MemberAttributeValue> findByMemberAndAttributeDef(Member member, MemberAttributeDef attributeDef);

    List<MemberAttributeValue> findByMember(Member member);

    List<MemberAttributeValue> findByAttributeDef(MemberAttributeDef attributeDef);

    void delete(MemberAttributeValue value);

    void deleteByMember(Member member);
}
