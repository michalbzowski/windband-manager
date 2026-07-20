package pl.michalbzowski.windband.domain.member;

import org.springframework.data.jpa.repository.Query;

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

    Optional<Group> findByDynamicSourceTypeAndDynamicSourceKey(DynamicSourceType type, String key);

    @Query("SELECT COUNT(gm) FROM GroupMember gm WHERE gm.group.dynamicSourceType = :type AND gm.group.dynamicSourceKey = :key")
    long countMembersByDynamicSourceTypeAndKey(DynamicSourceType type, String key);

    /**
     * True iff there is already a group with the given (band, name) pair. Used to
     * resolve name collisions BEFORE attempting an INSERT (so we can pick a
     * non-conflicting name like "Foo (2)" without relying on a DB exception that
     * would poison the surrounding transaction).
     */
    boolean existsByNameAndBandId(String name, Long bandId);

    void delete(Group group);

    void flush();
}
