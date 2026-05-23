package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.inventory.InstrumentItem;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;

public interface SpringDataInstrumentItemRepository extends JpaRepository<InstrumentItem, Long> {
    List<InstrumentItem> findByAssignedMember(Member member);
}
