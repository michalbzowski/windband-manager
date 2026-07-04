package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.member.Group;

import java.util.List;
import java.util.Optional;

public interface SpringDataGroupRepository extends JpaRepository<Group, Long> {

    List<Group> findAllByOrderByNameAsc();

    @Query("SELECT DISTINCT g FROM Group g LEFT JOIN FETCH g.members gm LEFT JOIN FETCH gm.member")
    List<Group> findAllWithMembers();

    List<Group> findAllByBandIdOrderByNameAsc(Long bandId);

    @Query("SELECT DISTINCT g FROM Group g LEFT JOIN FETCH g.members gm LEFT JOIN FETCH gm.member WHERE g.band.id = :bandId")
    List<Group> findAllWithMembersByBandId(@Param("bandId") Long bandId);

    Optional<Group> findByDynamicSource(MemberAttributeDef source);
}
