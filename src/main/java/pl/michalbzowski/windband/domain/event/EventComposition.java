package pl.michalbzowski.windband.domain.event;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.composition.Composition;

import java.time.Instant;

/**
 * Row in the {@code event_compositions} junction table: links a composition ("utwór")
 * into a band event's setlist with a 1-based position. The same composition may appear
 * on multiple events (e.g. a gala re-performs a piece from an earlier concert) and the
 * same event may have many pieces, so this is a pure N-to-M with an ordering tie-breaker.
 *
 * <p>Band-scope invariant: the event's band and the composition's band MUST agree before
 * a row can be persisted — verified in {@code EventCommandService.assignComposition} by
 * resolving both bands and refusing cross-band writes (same pattern as {@code
 * CompositionInstrument.forComposition}). This junction row itself keeps no direct
 * band FK, which is why this guard lives at the service layer, not in the schema.
 *
 * <p>{@code orderInSet}: 1-based position in the event's setlist so that "send all pages
 * to member X" later can iterate them in musical order (first piece to last). The UI
 * auto-appends new links at the end; no re-order API is exposed in this story.
 *
 * <p>This row carries no additional metadata (e.g. tempo, conductor) because those belong
 * on a per-performance annotation that is out of scope for this US-7.2 — it is deliberately
 * thin so the follow-on stories can grow it without breaking existing rows.
 */
@Entity
@Table(name = "event_compositions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "composition_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventComposition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private BandEvent event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "composition_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Composition composition;

    /** 1-based position in the event's setlist. */
    @Column(nullable = false)
    private int orderInSet;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected EventComposition(BandEvent event, Composition composition, int orderInSet) {
        this.event = event;
        this.composition = composition;
        this.orderInSet = orderInSet;
        this.createdAt = Instant.now();
    }

    /**
     * Entry point for service-layer calls. Validates the invariants BEFORE the instance is
     * handed out (same pattern as {@code CompositionInstrument.forComposition}).
     */
    public static EventComposition link(BandEvent event, Composition composition, int orderInSet) {
        if (event == null) throw new NullPointerException("event required");
        if (composition == null) throw new NullPointerException("composition required");
        if (orderInSet < 1) {
            throw new IllegalArgumentException("orderInSet must be >= 1 (got " + orderInSet + ")");
        }
        return new EventComposition(event, composition, orderInSet);
    }
}
