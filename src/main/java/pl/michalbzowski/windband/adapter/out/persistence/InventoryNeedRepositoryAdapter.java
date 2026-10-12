package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.inventory.InventoryNeed;
import pl.michalbzowski.windband.domain.inventory.InventoryNeedRepository;
import pl.michalbzowski.windband.domain.inventory.ItemType;
import pl.michalbzowski.windband.domain.inventory.NeedStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class InventoryNeedRepositoryAdapter implements InventoryNeedRepository {

    private final SpringDataInventoryNeedRepository springDataRepo;

    @Override
    public InventoryNeed save(InventoryNeed need) {
        return springDataRepo.save(need);
    }

    @Override
    public Optional<InventoryNeed> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public List<InventoryNeed> findAll() {
        return springDataRepo.findAll();
    }

    @Override
    public List<InventoryNeed> findByBandId(Long bandId) {
        return springDataRepo.findByBandId(bandId);
    }

    @Override
    public List<InventoryNeed> findByBandIdAndStatus(Long bandId, NeedStatus status) {
        return springDataRepo.findByBandIdAndStatus(bandId, status);
    }

    @Override
    public List<InventoryNeed> findByBandIdAndItemType(Long bandId, ItemType itemType) {
        return springDataRepo.findByBandIdAndItemType(bandId, itemType);
    }

    @Override
    public List<InventoryNeed> findByRequestedByMemberId(Long memberId) {
        return springDataRepo.findByRequestedById(memberId);
    }

    @Override
    public List<InventoryNeed> findByDateRange(LocalDate from, LocalDate to) {
        return springDataRepo.findByOrderedAtBetween(from, to);
    }

    @Override
    public List<InventoryNeed> findOpenNeeds(Long bandId) {
        return springDataRepo.findOpenByBandId(bandId);
    }

    @Override
    public List<InventoryNeed> findOverdueOrders(Long bandId) {
        return springDataRepo.findOverdueCandidatesByBandId(bandId);
    }

    @Override
    public void delete(InventoryNeed need) {
        springDataRepo.delete(need);
    }
}
