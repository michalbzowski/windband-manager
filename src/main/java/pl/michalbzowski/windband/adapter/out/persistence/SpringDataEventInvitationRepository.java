package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.event.EventInvitation;
import pl.michalbzowski.windband.domain.event.NotificationStatus;

import java.util.List;
import java.util.Optional;

public interface SpringDataEventInvitationRepository extends JpaRepository<EventInvitation, Long> {

    Optional<EventInvitation> findByToken(String token);

    List<EventInvitation> findByBandEventId(Long eventId);

    List<EventInvitation> findByBandEventIdAndMemberId(Long eventId, Long memberId);

    List<EventInvitation> findByBandEventIdAndStatus(Long eventId, NotificationStatus status);

    long countByBandEventIdAndStatusNot(Long eventId, NotificationStatus status);
}