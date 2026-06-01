package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class InstrumentRepositoryAdapter implements InstrumentRepository {

    private final SpringDataInstrumentRepository springDataRepo;

    @Override
    public Instrument save(Instrument instrument) {
        return springDataRepo.save(instrument);
    }

    @Override
    public Optional<Instrument> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public List<Instrument> findAll() {
        return springDataRepo.findAll();
    }

    @Override
    public List<Instrument> findAllOrderBySortPriority() {
        return springDataRepo.findAllByOrderBySortPriorityAsc();
    }

    @Override
    public Optional<Instrument> findByName(String name) {
        return springDataRepo.findByName(name);
    }
}
