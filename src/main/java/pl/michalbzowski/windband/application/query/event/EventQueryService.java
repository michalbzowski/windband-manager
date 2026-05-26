package pl.michalbzowski.windband.application.query.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.event.EventNotFoundException;
import pl.michalbzowski.windband.application.dto.EventDetailDto;
import pl.michalbzowski.windband.application.dto.EventDetailDto.ParticipationDto;
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

    public EventDetailDto getEventDetailById(Long id) {
        BandEvent event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));
        List<ParticipationDto> participationDtos = event.getParticipations().stream()
                .map(p -> new ParticipationDto(
                        p.getId(),
                        p.getMember().getFirstName() + " " + p.getMember().getLastName(),
                        p.getResponse().name(),
                        p.getPaymentAmount(),
                        p.getPaymentStatus().name()
                ))
                .toList();
        return new EventDetailDto(
                event.getId(),
                event.getName(),
                event.getDate(),
                event.getStartTime(),
                event.getLocation(),
                event.getEventType().name(),
                event.getNotes(),
                event.getConfirmedCount(),
                event.getDeclinedCount(),
                event.getNoResponseCount(),
                participationDtos
        );
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
