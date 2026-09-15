package pl.michalbzowski.windband.domain.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.band.Band;

import java.util.Objects;

/**
 * A band-scoped instrument (e.g. "Trąbka", "Flet"), optionally an alias of a primary
 * instrument in the same band (e.g. "Kornet" → "Trąbka"). Root instruments do not have
 * {@link #aliasOf}; aliases form at most a 1-step relationship in this data model —
 * see US-1.2 of the score-library plan ({@code docs/plans/biblioteka-utworow-user-stories.md}).
 */
@Entity
@Table(name = "instruments", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"band_id", "name"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "sort_priority")
    private Integer sortPriority = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "band_id")
    private Band band;

    /**
     * Self-reference that marks this instrument as an alias of {@code aliasOf} (e.g. "Kornet" → "Trąbka").
     * Nullable, lazy, always points to an instrument in the same band. The inverse navigation is
     * denormalised by the persistence layer into the read paths
     * ({@code InstrumentsByBandId}, {@code InstrumentRepository}) so a direct reverse-collection field
     * on this entity is deliberately omitted (avoids {@code @OneToMany(mappedBy=\"aliasOf\")} self-reference
     * maintenance cost and keeps the write path explicit: only {@link #setAliasOf(Instrument)} touches it).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alias_of_id")
    private Instrument aliasOf;

    private Instrument(String name) {
        this.name = Objects.requireNonNull(name, "instrument name required");
    }

    public static Instrument create(String name) {
        return new Instrument(name);
    }

    public static Instrument create(String name, Band band) {
        Instrument instrument = new Instrument(name);
        instrument.band = band;
        return instrument;
    }

    public void assignBand(Band band) {
        this.band = band;
    }

    public void updateName(String name) {
        this.name = Objects.requireNonNull(name, "instrument name required");
    }

    public void updateDescription(String description) {
        this.description = description;
    }

    public void updateSortPriority(Integer sortPriority) {
        this.sortPriority = sortPriority != null ? sortPriority : 0;
    }

    public boolean belongsToBand(Long bandId) {
        if (bandId == null) {
            return band == null;
        }
        return band != null && bandId.equals(band.getId());
    }

    /**
     * Sets this instrument as an alias of {@code target}.
     * <p>
     * Invariants enforced by the caller / persistence layer (deliberately not enforced here — this
     * class is a passive value holder; see {@link AliasValidationException} and
     * {@link InstrumentCommandService#updateAliasOf(Long, Long, Long)}):
     * </p>
     * <ul>
     *   <li>{@code target != null} — root instruments have {@code aliasOf == null};</li>
     *   <li>{@code target != this} — an instrument cannot alias itself (cycle guard);</li>
     *   <li>{@code target.aliasOf == null && target.band equals this.band} when both are non-null</li>
     *   <li>(band equality enforced at the band scope — never across bands).</li>
     * </ul>
     * <p>
     * Band equality is a soft invariant on the domain entity: the production write-path
     * ({@code InstrumentCommandService.updateAliasOf}) performs this check and rejects any
     * cross-band write with {@link AliasValidationException}. The DB-level FK
     * ({@code V37__add_alias_of_to_instrument.sql} adds a self-referential foreign key that the
     * persistence adapter is expected to scope — see migration note) provides the second net.
     * </p>
     */
    public void setAliasOf(Instrument target) {
        if (target == this) {
            throw new AliasValidationException("Instrument cannot be an alias of itself", this, target);
        }
        // Band equality: enforce if both sides expose a resolvable band.
        Long myBand = this.band == null ? null : this.band.getId();
        Long otherBand = target.getBand() == null ? null : target.getBand().getId();
        if (myBand != null && otherBand != null && !myBand.equals(otherBand)) {
            throw new AliasValidationException(
                    "Alias instrumentation must stay within the same band: my band=" + myBand
                            + " differs from alias band=" + otherBand, this, target);
        }
        // A root instrument cannot become the target of an alias relationship unless it is itself the target.
        // (If target has its own alias, we are building a chain — US-1.2 explicitly limits to 1-step aliases,
        // so reject: caller should point at the grandparent or re-plan.)
        if (target.getAliasOf() != null) {
            throw new AliasValidationException(
                    "Alias must point at a root instrument; '" + target.getName() + "' is itself an alias of another",
                    this, target);
        }
        this.aliasOf = target;
    }

    /** Convenience: clears the alias relationship (turns this back into a root). */
    public void clearAlias() {
        this.aliasOf = null;
    }

    /** {@code true} when this instrument is not an alias of another. */
    public boolean isRoot() {
        return this.aliasOf == null;
    }

    /** {@code true} when this instrument is an alias of another. */
    public boolean isAlias() {
        return this.aliasOf != null;
    }

    /** The primary instrument this row aliases, or {@code null} when this is a root. */
    public Instrument getCanonicalInstrument() {
        return this.aliasOf;
    }

    // SpotBugs/Checkstyle: no public factory for the alias-of self-reference — use setAliasOf(...).
    /**
     * Thrown when a write violates the alias invariants (self-alias, cross-band alias, or an alias chain).
     * The message is safe to surface verbatim in UI error text (contains instrument names only, no stack).
     */
    public static class AliasValidationException extends IllegalStateException {
        private final Instrument source;
        private final Instrument target;

        public AliasValidationException(String message, Instrument source, Instrument target) {
            super(message);
            this.source = source;
            this.target = target;
        }

        public Instrument getSource() {
            return source;
        }

        public Instrument getTarget() {
            return target;
        }
    }
}
