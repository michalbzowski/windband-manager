package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.michalbzowski.windband.domain.inventory.InstrumentItem;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;

public interface SpringDataInstrumentItemRepository extends JpaRepository<InstrumentItem, Long> {
    List<InstrumentItem> findByAssignedMember(Member member);

    @Query("SELECT i FROM InstrumentItem i LEFT JOIN FETCH i.assignedMember WHERE i.band.id = :bandId")
    List<InstrumentItem> findByBandId(@Param("bandId") Long bandId);

    @Query("SELECT i FROM InstrumentItem i LEFT JOIN FETCH i.assignedMember")
    List<InstrumentItem> findAllWithMember();
}
