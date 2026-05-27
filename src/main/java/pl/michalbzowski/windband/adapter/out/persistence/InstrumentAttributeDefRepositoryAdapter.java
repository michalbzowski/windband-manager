package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.InstrumentAttributeDef;
import pl.michalbzowski.windband.domain.inventory.InstrumentAttributeDefRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class InstrumentAttributeDefRepositoryAdapter implements InstrumentAttributeDefRepository {

    private final SpringDataInstrumentAttributeDefRepository springDataRepo;

    @Override
    public InstrumentAttributeDef save(InstrumentAttributeDef def) {
        return springDataRepo.save(def);
    }

    @Override
    public List<InstrumentAttributeDef> findByBand(Band band) {
        return springDataRepo.findByBandOrderByName(band);
    }

    @Override
    public Optional<InstrumentAttributeDef> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public void delete(InstrumentAttributeDef def) {
        springDataRepo.delete(def);
    }
}
