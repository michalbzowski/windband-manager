package pl.michalbzowski.windband.domain.inventory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.user.AppUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Service/maintenance record for instruments.
 * Tracks all service events: repairs, maintenance, calibration, cleaning, overhauls.
 */
@Entity
@Table(name = "instrument_service_records")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstrumentServiceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private InventoryItem instrument; // must be type INSTRUMENT

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false)
    private ServiceType serviceType;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    @Column(name = "service_provider", nullable = false)
    private String serviceProvider; // shop, technician, company name

    @Column(name = "provider_contact")
    private String providerContact; // phone, email, address

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description; // what was done

    @Column(name = "parts_replaced", columnDefinition = "TEXT")
    private String partsReplaced; // list of parts

    @Column(name = "cost", precision = 12, scale = 2)
    private BigDecimal cost;

    @Column(name = "warranty_until")
    private LocalDate warrantyUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority")
    private ServicePriority priority = ServicePriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ServiceStatus status = ServiceStatus.SCHEDULED;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Next recommended service date
    @Column(name = "next_service_date")
    private LocalDate nextServiceDate;

    // Next recommended service type
    @Enumerated(EnumType.STRING)
    @Column(name = "next_service_type")
    private ServiceType nextServiceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private AppUser requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private AppUser approvedBy;

    @Column(name = "approved_at")
    private LocalDate approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by_user_id")
    private AppUser completedBy;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "band_id", nullable = false)
    private Band band;

    public InstrumentServiceRecord(InventoryItem instrument, ServiceType serviceType,
                                    LocalDate serviceDate, String serviceProvider,
                                    String description, AppUser requestedBy, Band band) {
        this.instrument = Objects.requireNonNull(instrument, "instrument required");
        if (instrument.getType() != ItemType.INSTRUMENT) {
            throw new IllegalArgumentException("InstrumentServiceRecord only for INSTRUMENT type items");
        }
        this.serviceType = Objects.requireNonNull(serviceType, "serviceType required");
        this.serviceDate = Objects.requireNonNull(serviceDate, "serviceDate required");
        this.serviceProvider = Objects.requireNonNull(serviceProvider, "serviceProvider required");
        this.description = Objects.requireNonNull(description, "description required");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy required");
        this.band = Objects.requireNonNull(band, "band required");
        this.status = ServiceStatus.SCHEDULED;
    }

    public void approve(AppUser approver) {
        this.approvedBy = Objects.requireNonNull(approver);
        this.approvedAt = LocalDate.now();
        this.status = ServiceStatus.APPROVED;
    }

    public void startService() {
        this.status = ServiceStatus.IN_PROGRESS;
    }

    public void complete(AppUser completer, String completionNotes, LocalDate nextServiceDate, ServiceType nextServiceType) {
        this.completedBy = Objects.requireNonNull(completer);
        this.completedAt = LocalDateTime.now();
        this.completedDate = LocalDate.now();
        this.status = ServiceStatus.COMPLETED;
        if (completionNotes != null) {
            this.notes = (this.notes != null ? this.notes + "\n" : "") + "Completion: " + completionNotes;
        }
        if (nextServiceDate != null) this.nextServiceDate = nextServiceDate;
        if (nextServiceType != null) this.nextServiceType = nextServiceType;
    }

    public void cancel(AppUser canceller, String reason) {
        this.status = ServiceStatus.CANCELLED;
        this.notes = (this.notes != null ? this.notes + "\n" : "") + "Cancelled: " + reason;
    }

    public boolean isOpen() {
        return status == ServiceStatus.SCHEDULED || status == ServiceStatus.APPROVED || status == ServiceStatus.IN_PROGRESS;
    }

    public boolean isOverdue() {
        return serviceDate.isBefore(LocalDate.now()) && isOpen();
    }
}