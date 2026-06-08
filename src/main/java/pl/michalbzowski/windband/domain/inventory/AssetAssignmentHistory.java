package pl.michalbzowski.windband.domain.inventory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.user.AppUser;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Tracks the full assignment history of a single inventory item (uniform or instrument).
 * Each record represents one assignment period: who had it, who assigned it, from when, to when,
 * and the condition at assignment and return time.
 * Active assignment has returnedAt = null.
 * Enables reports: per-member inventory, per-item history, utilization stats,
 * and full audit trail of who was responsible for what and when.
 */
@Entity
@Table(name = "asset_assignment_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetAssignmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "uniform_item_id")
    private UniformItem uniformItem;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "instrument_item_id")
    private InstrumentItem instrumentItem;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_user_id")
    private AppUser assignedBy;

    @Column(nullable = false)
    private LocalDate assignedAt;

    private LocalDate returnedAt;

    @Column(nullable = false)
    private boolean active;

    private String conditionAtAssign;

    private String conditionAtReturn;

    private String notes;

    private AssetAssignmentHistory(UniformItem item, Member member, AppUser assignedBy,
                                   String conditionAtAssign, String notes) {
        this.uniformItem = Objects.requireNonNull(item, "item required");
        this.instrumentItem = null;
        this.member = Objects.requireNonNull(member, "member required");
        this.assignedBy = assignedBy;
        this.assignedAt = LocalDate.now();
        this.active = true;
        this.conditionAtAssign = conditionAtAssign;
        this.notes = notes;
    }

    private AssetAssignmentHistory(InstrumentItem item, Member member, AppUser assignedBy,
                                   String conditionAtAssign, String notes) {
        this.instrumentItem = Objects.requireNonNull(item, "item required");
        this.uniformItem = null;
        this.member = Objects.requireNonNull(member, "member required");
        this.assignedBy = assignedBy;
        this.assignedAt = LocalDate.now();
        this.active = true;
        this.conditionAtAssign = conditionAtAssign;
        this.notes = notes;
    }

    public static AssetAssignmentHistory forUniform(UniformItem item, Member member, AppUser assignedBy,
                                                    String conditionAtAssign, String notes) {
        return new AssetAssignmentHistory(item, member, assignedBy, conditionAtAssign, notes);
    }

    public static AssetAssignmentHistory forInstrument(InstrumentItem item, Member member, AppUser assignedBy,
                                                       String conditionAtAssign, String notes) {
        return new AssetAssignmentHistory(item, member, assignedBy, conditionAtAssign, notes);
    }

    public void markReturned(String conditionAtReturn, String notes) {
        this.returnedAt = LocalDate.now();
        this.active = false;
        this.conditionAtReturn = conditionAtReturn;
        if (notes != null) this.notes = notes;
    }

    public boolean isForUniform() {
        return uniformItem != null;
    }

    public boolean isForInstrument() {
        return instrumentItem != null;
    }

    public Long getItemId() {
        return isForUniform() ? uniformItem.getId() : instrumentItem.getId();
    }

    public String getItemName() {
        return isForUniform() ? uniformItem.getName() : instrumentItem.getName();
    }

    public String getAssignedByName() {
        if (assignedBy == null) return null;
        String displayName = assignedBy.getDisplayName();
        return (displayName != null && !displayName.isBlank()) ? displayName : assignedBy.getUsername();
    }
}
