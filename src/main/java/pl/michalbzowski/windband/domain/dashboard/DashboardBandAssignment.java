package pl.michalbzowski.windband.domain.dashboard;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.user.AppUser;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Assignment of a Superset dashboard to a band.
 * When autoAssignNew=true, any newly created band gets this dashboard automatically.
 */
@Entity
@Table(name = "dashboard_band_assignments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"dashboard_id", "band_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DashboardBandAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dashboard_id", nullable = false)
    private SupersetDashboard dashboard;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "band_id", nullable = false)
    private Band band;

    @Column(name = "auto_assign_new", nullable = false)
    private boolean autoAssignNew;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_user_id")
    private AppUser assignedBy;

    private DashboardBandAssignment(SupersetDashboard dashboard, Band band, boolean autoAssignNew, AppUser assignedBy) {
        this.dashboard = Objects.requireNonNull(dashboard, "dashboard required");
        this.band = Objects.requireNonNull(band, "band required");
        this.autoAssignNew = autoAssignNew;
        this.assignedAt = LocalDateTime.now();
        this.assignedBy = assignedBy;
    }

    public static DashboardBandAssignment create(SupersetDashboard dashboard, Band band, boolean autoAssignNew, AppUser assignedBy) {
        return new DashboardBandAssignment(dashboard, band, autoAssignNew, assignedBy);
    }

    public void setAutoAssignNew(boolean autoAssignNew) {
        this.autoAssignNew = autoAssignNew;
    }
}
