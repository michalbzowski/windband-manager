package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.event.EventInvitation;
import pl.michalbzowski.windband.domain.event.EventInvitationRepository;
import pl.michalbzowski.windband.domain.event.NotificationStatus;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EventInvitationRepositoryAdapter implements EventInvitationRepository {

    private final SpringDataEventInvitationRepository springDataRepo;

    @Override
    public EventInvitation save(EventInvitation invitation) {
        return springDataRepo.save(invitation);
    }

    @Override
    public Optional<EventInvitation> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public Optional<EventInvitation> findByToken(String token) {
        return springDataRepo.findByToken(token);
    }

    @Override
    public List<EventInvitation> findByEventId(Long eventId) {
        return springDataRepo.findByBandEventId(eventId);
    }

    @Override
    public List<EventInvitation> findByEventIdAndMemberId(Long eventId, Long memberId) {
        return springDataRepo.findByBandEventIdAndMemberId(eventId, memberId);
    }

    @Override
    public List<EventInvitation> findByEventIdAndStatus(Long eventId, NotificationStatus status) {
        return springDataRepo.findByBandEventIdAndStatus(eventId, status);
    }

    @Override
    public long countByEventIdAndStatusNot(Long eventId, NotificationStatus status) {
        return springDataRepo.countByBandEventIdAndStatusNot(eventId, status);
    }

    @Override
    public void delete(EventInvitation invitation) {
        springDataRepo.delete(invitation);
    }
}