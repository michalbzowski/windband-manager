package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.inventory.AssetAssignmentHistory;
import pl.michalbzowski.windband.domain.inventory.InstrumentItem;
import pl.michalbzowski.windband.domain.inventory.UniformItem;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;

public interface SpringDataAssetAssignmentHistoryRepository extends JpaRepository<AssetAssignmentHistory, Long> {
    List<AssetAssignmentHistory> findByUniformItemOrderByAssignedAtDesc(UniformItem item);
    List<AssetAssignmentHistory> findByInstrumentItemOrderByAssignedAtDesc(InstrumentItem item);
    List<AssetAssignmentHistory> findByMemberOrderByAssignedAtDesc(Member member);
    List<AssetAssignmentHistory> findByActiveTrue();
}
