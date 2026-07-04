package pl.michalbzowski.windband.application.query.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.event.EventNotFoundException;
import pl.michalbzowski.windband.application.dto.EventDetailDto;
import pl.michalbzowski.windband.application.dto.EventDetailDto.InstrumentCountDto;
import pl.michalbzowski.windband.application.dto.EventDetailDto.ParticipationDto;
import pl.michalbzowski.windband.application.dto.GroupSummaryDto;
import pl.michalbzowski.windband.application.query.member.GroupQueryService;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventRepository;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class EventQueryService {

    private final EventRepository eventRepository;
    private final GroupQueryService groupQueryService;
    private final InstrumentRepository instrumentRepository;

    public EventQueryService(EventRepository eventRepository, GroupQueryService groupQueryService, 
                             InstrumentRepository instrumentRepository) {
        this.eventRepository = eventRepository;
        this.groupQueryService = groupQueryService;
        this.instrumentRepository = instrumentRepository;
    }

    public BandEvent getEventById(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));
    }

    public EventDetailDto getEventDetailById(Long id, Long bandId) {
        BandEvent event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));
        List<ParticipationDto> participationDtos = event.getParticipations().stream()
                .map(p -> new ParticipationDto(
                        p.getId(),
                        p.getMember().getId(),
                        p.getMember().getFirstName() + " " + p.getMember().getLastName(),
                        p.getInstrument() != null ? p.getInstrument().getName()
                            : p.getMember().getPrimaryInstrument().map(i -> i.getName()).orElse(null),
                        p.getResponse().name(),
                        p.getPaymentAmount(),
                        p.getPaymentStatus().name()
                ))
                .toList();

        // Build instrument priority map (lower number = higher priority)
        var instrumentPriorities = instrumentRepository.findAllOrderBySortPriority().stream()
                .collect(java.util.stream.Collectors.toMap(
                        pl.michalbzowski.windband.domain.member.Instrument::getName,
                        pl.michalbzowski.windband.domain.member.Instrument::getSortPriority,
                        (existing, replacement) -> existing,
                        java.util.LinkedHashMap::new
                ));

        // Sort participations by instrument priority, then by member name
        List<ParticipationDto> sortedParticipationDtos = participationDtos.stream()
                .sorted(Comparator
                        .<ParticipationDto>comparingInt(p -> {
                            Integer priority = instrumentPriorities.get(p.instrumentName());
                            return priority != null ? priority : Integer.MAX_VALUE;
                        })
                        .thenComparing(ParticipationDto::memberName))
                .toList();

        List<GroupSummaryDto> groups = groupQueryService.getAllGroups(bandId).stream()
                .map(g -> new GroupSummaryDto(g.id(), g.name(), g.description(), g.memberCount(), g.dynamic()))
                .toList();

        // Calculate instrument summary from confirmed participants
        List<InstrumentCountDto> instrumentSummary = participationDtos.stream()
                .filter(p -> "CONFIRMED".equals(p.response()))
                .collect(Collectors.groupingBy(ParticipationDto::instrumentName, Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getKey() != null)
                .map(e -> new InstrumentCountDto(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(InstrumentCountDto::count).reversed())
                .toList();

        return new EventDetailDto(
                event.getId(),
                event.getName(),
                event.getDate(),
                event.getStartTime(),
                event.getLocation(),
                event.getEventType().name(),
                event.getPaymentType().name(),
                event.getPaymentAmount(),
                event.getPayoutPerMember(),
                event.getNotes(),
                event.getConfirmedCount(),
                event.getDeclinedCount(),
                event.getNoResponseCount(),
                sortedParticipationDtos,
                groups,
                instrumentSummary
        );
    }

    public List<BandEvent> getAllEvents() {
        return getAllEvents(null);
    }

    public List<BandEvent> getAllEvents(Long teamId) {
        if (teamId != null) {
            return eventRepository.findAllOrderByDateDescByBandId(teamId);
        }
        return eventRepository.findAllOrderByDateDesc();
    }

    public List<BandEvent> getEventsBetween(LocalDate from, LocalDate to) {
        return getEventsBetween(from, to, null);
    }

    public List<BandEvent> getEventsBetween(LocalDate from, LocalDate to, Long teamId) {
        if (teamId != null) {
            return eventRepository.findByDateBetweenAndBandId(from, to, teamId);
        }
        return eventRepository.findByDateBetween(from, to);
    }

    public long getEventCountBetween(LocalDate from, LocalDate to) {
        return getEventCountBetween(from, to, null);
    }

    public long getEventCountBetween(LocalDate from, LocalDate to, Long teamId) {
        return getEventsBetween(from, to, teamId).size();
    }
}
