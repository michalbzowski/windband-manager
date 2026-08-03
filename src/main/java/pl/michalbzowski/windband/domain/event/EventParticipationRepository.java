package pl.michalbzowski.windband.domain.event;

import java.util.Optional;

public interface EventParticipationRepository {

    EventParticipation save(EventParticipation participation);

    Optional<EventParticipation> findByEventIdAndMemberId(Long eventId, Long memberId);

    void delete(EventParticipation participation);
}
