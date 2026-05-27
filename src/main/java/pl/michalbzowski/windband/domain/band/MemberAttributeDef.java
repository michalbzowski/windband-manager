package pl.michalbzowski.windband.domain.band;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "member_attribute_defs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"band_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAttributeDef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "band_id", nullable = false)
    private Band band;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type; // "BOOLEAN" or "TEXT"

    @Column(nullable = false)
    private boolean required;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private LocalDate createdAt;

    @Column(length = 2000)
    private String options; // JSON array for SELECT/MULTI_SELECT types

    private MemberAttributeDef(Band band, String name, String type, boolean required, int displayOrder, String options) {
        this.band = Objects.requireNonNull(band, "band required");
        this.name = Objects.requireNonNull(name, "name required");
        this.type = Objects.requireNonNull(type, "type required");
        this.required = required;
        this.displayOrder = displayOrder;
        this.active = true;
        this.createdAt = LocalDate.now();
    }

    public static MemberAttributeDef create(Band band, String name, String type, boolean required, int displayOrder, String options) {
        return new MemberAttributeDef(band, name, type, required, displayOrder, options);
    }

    public void update(String name, String type, boolean required, int displayOrder, String options) {
        this.name = Objects.requireNonNull(name, "name required");
        this.type = Objects.requireNonNull(type, "type required");
        this.required = required;
        this.displayOrder = displayOrder;
        this.options = options;
    }
}
