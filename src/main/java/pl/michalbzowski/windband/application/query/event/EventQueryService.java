package pl.michalbzowski.windband.application.query.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.event.EventNotFoundException;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventRepository;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventQueryService {

    private final EventRepository eventRepository;

    public BandEvent getEventById(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));
    }

    public List<BandEvent> getAllEvents() {
        return eventRepository.findAllOrderByDateDesc();
    }

    public List<BandEvent> getEventsBetween(LocalDate from, LocalDate to) {
        return eventRepository.findByDateBetween(from, to);
    }

    public long getEventCountBetween(LocalDate from, LocalDate to) {
        return eventRepository.findByDateBetween(from, to).size();
    }
}
