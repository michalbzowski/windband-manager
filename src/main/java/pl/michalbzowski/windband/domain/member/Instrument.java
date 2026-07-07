package pl.michalbzowski.windband.domain.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.band.Band;

import java.util.Objects;

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
}
