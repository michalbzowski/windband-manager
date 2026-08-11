package pl.michalbzowski.windband.domain.inventory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.michalbzowski.windband.domain.band.Band;

import java.util.Objects;

/**
 * Warehouse / storage location for inventory items.
 * Can be a physical room, cabinet, container, or external location.
 */
@Entity
@Table(name = "warehouses")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WarehouseType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "band_id", nullable = false)
    private Band band;

    // Address for external warehouses
    private String address;
    private String contactPerson;
    private String phone;
    private String email;

    // Capacity and organization
    private Integer capacity; // approximate max items
    private String layoutNotes; // e.g., "Shelf A1-A5: Uniforms, Shelf B1-B3: Instruments"

    @Column(nullable = false)
    private boolean active = true;

    public Warehouse(String name, WarehouseType type, Band band) {
        this.name = Objects.requireNonNull(name, "name required");
        this.type = Objects.requireNonNull(type, "type required");
        this.band = Objects.requireNonNull(band, "band required");
    }

    public static Warehouse createInternal(String name, Band band) {
        return new Warehouse(name, WarehouseType.INTERNAL, band);
    }

    public static Warehouse createExternal(String name, Band band, String address, String contactPerson) {
        Warehouse w = new Warehouse(name, WarehouseType.EXTERNAL, band);
        w.address = address;
        w.contactPerson = contactPerson;
        return w;
    }

    public static Warehouse createArchive(String name, Band band) {
        return new Warehouse(name, WarehouseType.ARCHIVE, band);
    }

    public void deactivate() {
        this.active = false;
    }
}