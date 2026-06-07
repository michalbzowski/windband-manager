package pl.michalbzowski.windband.application.query.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.inventory.AwardItem;
import pl.michalbzowski.windband.domain.inventory.AwardItemRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AwardQueryService {

    private final AwardItemRepository awardItemRepository;

    public List<AwardItem> getAwardItemsForBand(Long bandId) {
        return awardItemRepository.findByBandIdOrderByDateAwardedDescNameAsc(bandId);
    }

    public List<AwardItem> getAssignedAwardItems(Long bandId) {
        return awardItemRepository.findByBandIdAndAssignedMemberIsNotNull(bandId);
    }
}
