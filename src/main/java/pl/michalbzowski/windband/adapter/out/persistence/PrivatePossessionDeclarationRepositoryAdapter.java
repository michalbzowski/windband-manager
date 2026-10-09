package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.inventory.DeclarationStatus;
import pl.michalbzowski.windband.domain.inventory.ItemType;
import pl.michalbzowski.windband.domain.inventory.PrivatePossessionDeclaration;
import pl.michalbzowski.windband.domain.inventory.PrivatePossessionDeclarationRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PrivatePossessionDeclarationRepositoryAdapter implements PrivatePossessionDeclarationRepository {

    private final SpringDataPrivatePossessionDeclarationRepository springDataRepo;

    @Override
    public PrivatePossessionDeclaration save(PrivatePossessionDeclaration declaration) {
        return springDataRepo.save(declaration);
    }

    @Override
    public Optional<PrivatePossessionDeclaration> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public List<PrivatePossessionDeclaration> findAll() {
        return springDataRepo.findAll();
    }

    @Override
    public List<PrivatePossessionDeclaration> findByMemberId(Long memberId) {
        return springDataRepo.findByMemberId(memberId);
    }

    @Override
    public List<PrivatePossessionDeclaration> findByBandId(Long bandId) {
        return springDataRepo.findByBandId(bandId);
    }

    @Override
    public List<PrivatePossessionDeclaration> findByBandIdAndStatus(Long bandId, DeclarationStatus status) {
        return springDataRepo.findByBandIdAndStatus(bandId, status);
    }

    @Override
    public List<PrivatePossessionDeclaration> findByItemTypeAndBandId(ItemType itemType, Long bandId) {
        return springDataRepo.findByItemTypeAndBandId(itemType, bandId);
    }

    @Override
    public List<PrivatePossessionDeclaration> findActiveByBandId(Long bandId) {
        return springDataRepo.findActiveByBandId(bandId);
    }

    @Override
    public List<PrivatePossessionDeclaration> findExpiringSoon(Long bandId, LocalDate beforeDate) {
        return springDataRepo.findExpiringSoon(bandId, beforeDate);
    }

    @Override
    public void delete(PrivatePossessionDeclaration declaration) {
        springDataRepo.delete(declaration);
    }
}
