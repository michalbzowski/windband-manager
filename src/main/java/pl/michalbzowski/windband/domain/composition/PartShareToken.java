package pl.michalbzowski.windband.domain.composition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * US-7.11 — opaque, public share token for one voice ("part") row.
 *
 * <p>The share link musicians receive is {@code /public/parts/{token}} — the token is a
 * random UUIDv4 (122 bits of entropy), so it cannot be iterated the way the old
 * {@code /bands/{bandId}/compositions/{id}/parts/{partId}} URL could. Revoke = rotate
 * ({@link #rotate()}); the part keeps exactly ONE live token at a time.</p>
 *
 * <p>Contract, enforced by the factory: the token is minted here (never caller-supplied)
 * and the association is non-null. The FK is {@code ON DELETE CASCADE} both in Hibernate
 * test DDL (@OnDelete) and in the production migration — deleting the part kills its link.</p>
 */
@Entity
@Table(name = "part_share_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uq_part_share_tokens_part", columnNames = "part_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartShareToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The opaque public credential. UUIDv4 — random, unguessable, sequential-ID-free. */
    @Column(name = "token", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID token;

    /** The voice this link serves. One token per part (unique above); cascade-delete below. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CompositionInstrument part;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Email (or "system") of whoever minted/rotated this token — audit only. */
    @Column(name = "created_by", length = 255)
    private String createdBy;

    /** Factory: mints a fresh random token for {@code part}, stamped with who/when. */
    public static PartShareToken issue(CompositionInstrument part, String createdBy, Instant now) {
        if (part == null || part.getId() == null) {
            throw new IllegalArgumentException("Part share token requires a persisted part.");
        }
        PartShareToken t = new PartShareToken();
        t.token     = UUID.randomUUID();
        t.part      = part;
        t.createdBy = createdBy;
        t.createdAt = now == null ? Instant.now() : now;
        return t;
    }

    /** Rotation (revoke): replaces the live credential in place; the old UUID stops resolving. */
    public PartShareToken rotate(String newCreatedBy, Instant now) {
        this.token     = UUID.randomUUID();
        this.createdBy = newCreatedBy;
        this.createdAt = now == null ? Instant.now() : now;
        return this;
    }
}
