package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.inventory.AwardItem;
import pl.michalbzowski.windband.domain.inventory.AwardItemRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AwardItemRepositoryAdapter implements AwardItemRepository {

    private final SpringDataAwardItemRepository springDataRepo;

    @Override
    public AwardItem save(AwardItem item) {
        return springDataRepo.save(item);
    }

    @Override
    public Optional<AwardItem> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public void delete(AwardItem item) {
        springDataRepo.delete(item);
    }

    @Override
    public List<AwardItem> findByBandIdOrderByDateAwardedDescNameAsc(Long bandId) {
        return springDataRepo.findByBandIdOrderByDateAwardedDescNameAsc(bandId);
    }

    @Override
    public List<AwardItem> findByBandIdAndAssignedMemberIsNotNull(Long bandId) {
        return springDataRepo.findByBandIdAndAssignedMemberIsNotNull(bandId);
    }
}
