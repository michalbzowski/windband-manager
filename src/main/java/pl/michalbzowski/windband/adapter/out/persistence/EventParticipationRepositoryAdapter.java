package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.event.EventParticipation;
import pl.michalbzowski.windband.domain.event.EventParticipationRepository;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EventParticipationRepositoryAdapter implements EventParticipationRepository {

    private final SpringDataEventParticipationRepository springDataRepo;

    @Override
    public EventParticipation save(EventParticipation participation) {
        return springDataRepo.save(participation);
    }

    @Override
    public Optional<EventParticipation> findByEventIdAndMemberId(Long eventId, Long memberId) {
        return springDataRepo.findByBandEventIdAndMemberId(eventId, memberId);
    }

    @Override
    public void delete(EventParticipation participation) {
        springDataRepo.delete(participation);
    }
}
