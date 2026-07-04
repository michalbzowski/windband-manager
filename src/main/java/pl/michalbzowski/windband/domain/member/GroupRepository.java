package pl.michalbzowski.windband.domain.member;

import pl.michalbzowski.windband.domain.band.MemberAttributeDef;

import java.util.List;
import java.util.Optional;

public interface GroupRepository {

    Group save(Group group);

    Optional<Group> findById(Long id);

    List<Group> findAllOrderByName();

    List<Group> findAllWithMembers();

    List<Group> findAllByBandId(Long bandId);

    List<Group> findAllWithMembersByBandId(Long bandId);

    Optional<Group> findByDynamicSource(MemberAttributeDef source);

    void delete(Group group);
}
