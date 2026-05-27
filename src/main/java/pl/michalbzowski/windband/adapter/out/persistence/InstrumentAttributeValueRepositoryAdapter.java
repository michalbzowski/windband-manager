package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.inventory.InstrumentAttributeDef;
import pl.michalbzowski.windband.domain.inventory.InstrumentAttributeValue;
import pl.michalbzowski.windband.domain.inventory.InstrumentAttributeValueRepository;
import pl.michalbzowski.windband.domain.inventory.InstrumentItem;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class InstrumentAttributeValueRepositoryAdapter implements InstrumentAttributeValueRepository {

    private final SpringDataInstrumentAttributeValueRepository springDataRepo;

    @Override
    public InstrumentAttributeValue save(InstrumentAttributeValue value) {
        return springDataRepo.save(value);
    }

    @Override
    public Optional<InstrumentAttributeValue> findByInstrumentItemAndAttributeDef(InstrumentItem item, InstrumentAttributeDef def) {
        return springDataRepo.findByInstrumentItemAndAttributeDef(item, def);
    }

    @Override
    public List<InstrumentAttributeValue> findByInstrumentItem(InstrumentItem item) {
        return springDataRepo.findByInstrumentItem(item);
    }

    @Override
    public void delete(InstrumentAttributeValue value) {
        springDataRepo.delete(value);
    }
}
