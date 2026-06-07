package pl.michalbzowski.windband.domain.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.michalbzowski.windband.domain.band.Band;

import java.util.List;

@Repository
public interface AwardItemRepository extends JpaRepository<AwardItem, Long> {
    List<AwardItem> findByBandIdOrderByDateAwardedDescNameAsc(Long bandId);
    List<AwardItem> findByBandIdAndAssignedMemberIsNotNull(Long bandId);
}
