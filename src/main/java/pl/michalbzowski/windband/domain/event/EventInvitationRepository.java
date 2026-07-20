package pl.michalbzowski.windband.domain.event;

import java.util.List;
import java.util.Optional;

public interface EventInvitationRepository {

    EventInvitation save(EventInvitation invitation);

    Optional<EventInvitation> findById(Long id);

    Optional<EventInvitation> findByToken(String token);

    List<EventInvitation> findByEventId(Long eventId);

    List<EventInvitation> findByEventIdAndMemberId(Long eventId, Long memberId);

    List<EventInvitation> findByEventIdAndStatus(Long eventId, NotificationStatus status);

    long countByEventIdAndStatusNot(Long eventId, NotificationStatus status);

    void delete(EventInvitation invitation);
}
