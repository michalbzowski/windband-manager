package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;
import pl.michalbzowski.windband.domain.rehearsal.RehearsalRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RehearsalRepositoryAdapter implements RehearsalRepository {

    private final SpringDataRehearsalRepository springDataRepo;

    @Override
    public Rehearsal save(Rehearsal rehearsal) {
        return springDataRepo.save(rehearsal);
    }

    @Override
    public Optional<Rehearsal> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public List<Rehearsal> findByDateBetween(LocalDate from, LocalDate to) {
        return springDataRepo.findByDateBetweenOrderByDateDesc(from, to);
    }

    @Override
    public List<Rehearsal> findByDateBetweenAndBandId(LocalDate from, LocalDate to, Long bandId) {
        return springDataRepo.findByDateBetweenAndBandIdOrderByDateDesc(from, to, bandId);
    }

    @Override
    public List<Rehearsal> findAllOrderByDateDesc() {
        return springDataRepo.findAllByOrderByDateDesc();
    }

    @Override
    public List<Rehearsal> findAllOrderByDateDescByBandId(Long bandId) {
        return springDataRepo.findAllByBandIdOrderByDateDesc(bandId);
    }

    @Override
    public void delete(Rehearsal rehearsal) {
        springDataRepo.delete(rehearsal);
    }
}
