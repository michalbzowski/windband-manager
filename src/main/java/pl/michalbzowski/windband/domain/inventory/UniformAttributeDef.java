package pl.michalbzowski.windband.domain.inventory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.band.Band;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "uniform_attribute_defs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"band_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UniformAttributeDef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "band_id", nullable = false)
    private Band band;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type; // BOOLEAN, TEXT, NUMBER, SELECT, MULTI_SELECT, DATE

    @Column(nullable = false)
    private boolean required;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "display_in_list", nullable = false)
    private boolean displayInList = false;

    @Column(nullable = false)
    private LocalDate createdAt;

    @Column(length = 2000)
    private String options; // comma-separated for SELECT/MULTI_SELECT

    private UniformAttributeDef(Band band, String name, String type, boolean required, int displayOrder, String options, boolean displayInList) {
        this.band = Objects.requireNonNull(band);
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.required = required;
        this.displayOrder = displayOrder;
        this.options = options;
        this.active = true;
        this.displayInList = displayInList;
        this.createdAt = LocalDate.now();
    }

    public static UniformAttributeDef create(Band band, String name, String type, boolean required, int displayOrder, String options) {
        return new UniformAttributeDef(band, name, type, required, displayOrder, options, false);
    }

    public static UniformAttributeDef create(Band band, String name, String type, boolean required, boolean displayInList, int displayOrder, String options) {
        return new UniformAttributeDef(band, name, type, required, displayOrder, options, displayInList);
    }

    public void update(String name, String type, boolean required, int displayOrder, String options) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.required = required;
        this.displayOrder = displayOrder;
        this.options = options;
    }
}
