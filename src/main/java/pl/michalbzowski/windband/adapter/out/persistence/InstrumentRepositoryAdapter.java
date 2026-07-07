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
    public void delete(Instrument instrument) {
        springDataRepo.delete(instrument);
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
    public List<Instrument> findAllOrderBySortPriorityByBandId(Long bandId) {
        return springDataRepo.findAllByBandIdOrderBySortPriorityAsc(bandId);
    }

    @Override
    public Optional<Instrument> findByName(String name) {
        return springDataRepo.findByName(name);
    }

    @Override
    public Optional<Instrument> findByNameAndBandId(String name, Long bandId) {
        return springDataRepo.findByNameAndBandId(name, bandId);
    }

    @Override
    public Optional<Instrument> findByIdAndBandId(Long id, Long bandId) {
        return springDataRepo.findByIdAndBandId(id, bandId);
    }
}
