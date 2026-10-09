package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.inventory.ExternalOwnerType;
import pl.michalbzowski.windband.domain.inventory.InventoryItem;
import pl.michalbzowski.windband.domain.inventory.InventoryItemRepository;
import pl.michalbzowski.windband.domain.inventory.ItemType;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class InventoryItemRepositoryAdapter implements InventoryItemRepository {

    private final SpringDataInventoryItemRepository springDataRepo;

    @Override
    public InventoryItem save(InventoryItem item) {
        return springDataRepo.save(item);
    }

    @Override
    public Optional<InventoryItem> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public Optional<InventoryItem> findBySystemId(String systemId) {
        return springDataRepo.findBySystemId(systemId);
    }

    @Override
    public List<InventoryItem> findAllByBandId(Long bandId) {
        return springDataRepo.findByBandId(bandId);
    }

    @Override
    public List<InventoryItem> findAllByBandIdAndType(Long bandId, ItemType itemType) {
        return springDataRepo.findByBandIdAndType(bandId, itemType);
    }

    @Override
    public List<InventoryItem> findWithMembersByBandId(Long bandId) {
        return springDataRepo.findWithMembersByBandId(bandId);
    }

    @Override
    public List<InventoryItem> findAllByAssignedMember(Member member) {
        return springDataRepo.findByAssignedMemberId(member.getId());
    }

    @Override
    public List<InventoryItem> findAllByLocation(Long warehouseId) {
        return springDataRepo.findByLocation(warehouseId);
    }

    @Override
    public List<InventoryItem> findByAssignedMemberId(Long memberId) {
        return springDataRepo.findByAssignedMemberId(memberId);
    }

    @Override
    public List<InventoryItem> findByExternalOwnerType(ExternalOwnerType externalOwnerType, Long bandId) {
        return springDataRepo.findByExternalOwnerTypeAndBandId(externalOwnerType, bandId);
    }

    @Override
    public void delete(InventoryItem item) {
        springDataRepo.delete(item);
    }
}
