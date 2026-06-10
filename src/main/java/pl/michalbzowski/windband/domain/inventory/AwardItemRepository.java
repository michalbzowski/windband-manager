package pl.michalbzowski.windband.domain.inventory;

import pl.michalbzowski.windband.domain.band.Band;

import java.util.List;
import java.util.Optional;

public interface AwardItemRepository {
    AwardItem save(AwardItem item);
    Optional<AwardItem> findById(Long id);
    void delete(AwardItem item);
    List<AwardItem> findByBandIdOrderByDateAwardedDescNameAsc(Long bandId);
    List<AwardItem> findByBandIdAndAssignedMemberIsNotNull(Long bandId);
}
