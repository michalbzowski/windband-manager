package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.AwardAttributeDef;
import pl.michalbzowski.windband.domain.inventory.AwardAttributeDefRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AwardAttributeDefRepositoryAdapter implements AwardAttributeDefRepository {

    private final SpringDataAwardAttributeDefRepository springDataRepo;

    @Override
    public AwardAttributeDef save(AwardAttributeDef def) {
        return springDataRepo.save(def);
    }

    @Override
    public Optional<AwardAttributeDef> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public void delete(AwardAttributeDef def) {
        springDataRepo.delete(def);
    }

    @Override
    public List<AwardAttributeDef> findByBandAndActiveTrueOrderByDisplayOrderAsc(Band band) {
        return springDataRepo.findByBandAndActiveTrueOrderByDisplayOrderAsc(band);
    }
}
