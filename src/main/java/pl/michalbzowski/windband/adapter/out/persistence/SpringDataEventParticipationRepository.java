package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.event.EventParticipation;

import java.util.Optional;

public interface SpringDataEventParticipationRepository extends JpaRepository<EventParticipation, Long> {

    Optional<EventParticipation> findByBandEventIdAndMemberId(Long eventId, Long memberId);
}
