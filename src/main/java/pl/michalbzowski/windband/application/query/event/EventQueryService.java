package pl.michalbzowski.windband.application.query.event;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.event.EventNotFoundException;
import pl.michalbzowski.windband.application.dto.EventDetailDto;
import pl.michalbzowski.windband.application.dto.EventDetailDto.InstrumentCountDto;
import pl.michalbzowski.windband.application.dto.EventDetailDto.ParticipationDto;
import pl.michalbzowski.windband.application.dto.GroupSummaryDto;
import pl.michalbzowski.windband.application.query.member.GroupQueryService;
import pl.michalbzowski.windband.application.service.ConsentService;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventInvitationRepository;
import pl.michalbzowski.windband.domain.event.EventRepository;
import pl.michalbzowski.windband.domain.member.ConsentType;
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
    private final EventInvitationRepository invitationRepository;
    private final ConsentService consentService;

    public EventQueryService(EventRepository eventRepository, GroupQueryService groupQueryService,
                             InstrumentRepository instrumentRepository,
                             EventInvitationRepository invitationRepository,
                             ConsentService consentService) {
        this.eventRepository = eventRepository;
        this.groupQueryService = groupQueryService;
        this.instrumentRepository = instrumentRepository;
        this.invitationRepository = invitationRepository;
        this.consentService = consentService;
    }

    public BandEvent getEventById(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));
    }

    public EventDetailDto getEventDetailById(Long id, Long bandId) {
        BandEvent event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));

        // Load invitation statuses for this event
        var invitations = invitationRepository.findByEventId(id);
        var invitationStatusByMember = new java.util.HashMap<Long, String>();
        for (var inv : invitations) {
            invitationStatusByMember.put(inv.getMember().getId(), inv.getStatus().name());
        }

        List<ParticipationDto> participationDtos = new java.util.ArrayList<>(event.getParticipations()).stream()
                .map(p -> {
                    Long memberId = p.getMember().getId();
                    String invStatus = invitationStatusByMember.getOrDefault(memberId, "NOT_SENT");
                    boolean hasEmail = p.getMember().getEmail() != null && !p.getMember().getEmail().isBlank();
                    // If member has no email, mark as FAILED (can't send)
                    if (!hasEmail && "NOT_SENT".equals(invStatus)) {
                        invStatus = "FAILED";
                    }
                    boolean consentGiven = consentService.isConsentGranted(p.getMember(), ConsentType.EVENTS);
                    return new ParticipationDto(
                            p.getId(),
                            memberId,
                            p.getMember().getFirstName() + " " + p.getMember().getLastName(),
                            p.getMember().getEmail(),
                            p.getInstrument() != null ? p.getInstrument().getName()
                                : p.getMember().getPrimaryInstrument().map(i -> i.getName()).orElse(null),
                            p.getResponse().name(),
                            p.getPaymentAmount(),
                            p.getPaymentStatus().name(),
                            invStatus,
                            consentGiven
                    );
                })
                .toList();

        // Build instrument priority map (lower number = higher priority)
        var instrumentPriorities = (bandId != null
                ? instrumentRepository.findAllOrderBySortPriorityByBandId(bandId)
                : instrumentRepository.findAllOrderBySortPriority()).stream()
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

    /**
     * Upcoming (today or later) events, sorted nearest-first (ascending by date).
     */
    public List<BandEvent> getUpcomingEvents(Long teamId) {
        LocalDate today = LocalDate.now();
        return loadSortedAsc(teamId).stream()
                .filter(e -> !e.getDate().isBefore(today))
                .collect(Collectors.toList());
    }

    /**
     * Past (before today) events, sorted most-recent-first (descending by date),
     * rendered after the upcoming section.
     */
    public List<BandEvent> getPastEvents(Long teamId) {
        LocalDate today = LocalDate.now();
        return loadSortedAsc(teamId).stream()
                .filter(e -> e.getDate().isBefore(today))
                .sorted(Comparator.comparing(BandEvent::getDate).reversed())
                .collect(Collectors.toList());
    }

    private List<BandEvent> loadSortedAsc(Long teamId) {
        List<BandEvent> all = (teamId != null)
                ? eventRepository.findAllOrderByDateDescByBandId(teamId)
                : eventRepository.findAllOrderByDateDesc();
        all.sort(Comparator.comparing(BandEvent::getDate));
        return all;
    }

    public long getEventCountBetween(LocalDate from, LocalDate to) {
        return getEventCountBetween(from, to, null);
    }

    public long getEventCountBetween(LocalDate from, LocalDate to, Long teamId) {
        return getEventsBetween(from, to, teamId).size();
    }
}
