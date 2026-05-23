package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EventRepositoryAdapter implements EventRepository {

    private final SpringDataEventRepository springDataRepo;

    @Override
    public BandEvent save(BandEvent event) {
        return springDataRepo.save(event);
    }

    @Override
    public Optional<BandEvent> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public List<BandEvent> findByDateBetween(LocalDate from, LocalDate to) {
        return springDataRepo.findByDateBetweenOrderByDateDesc(from, to);
    }

    @Override
    public List<BandEvent> findAllOrderByDateDesc() {
        return springDataRepo.findAllByOrderByDateDesc();
    }

    @Override
    public void delete(BandEvent event) {
        springDataRepo.delete(event);
    }
}
