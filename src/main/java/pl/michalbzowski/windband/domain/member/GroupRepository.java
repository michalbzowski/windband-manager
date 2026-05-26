package pl.michalbzowski.windband.domain.member;

import java.util.List;
import java.util.Optional;

public interface GroupRepository {

    Group save(Group group);

    Optional<Group> findById(Long id);

    List<Group> findAllOrderByName();

    List<Group> findAllWithMembers();

    void delete(Group group);
}
