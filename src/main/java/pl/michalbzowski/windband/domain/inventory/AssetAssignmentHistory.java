package pl.michalbzowski.windband.domain.inventory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.member.Member;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Tracks the full assignment history of a single inventory item (uniform or instrument).
 * Each record represents one assignment period: who had it, from when, to when.
 * Active assignment has returnedAt = null.
 * Enables reports: per-member inventory, per-item history, utilization stats.
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

    @Column(nullable = false)
    private LocalDate assignedAt;

    private LocalDate returnedAt;

    @Column(nullable = false)
    private boolean active;

    private String notes;

    private AssetAssignmentHistory(UniformItem item, Member member, String notes) {
        this.uniformItem = Objects.requireNonNull(item, "item required");
        this.instrumentItem = null;
        this.member = Objects.requireNonNull(member, "member required");
        this.assignedAt = LocalDate.now();
        this.active = true;
        this.notes = notes;
    }

    private AssetAssignmentHistory(InstrumentItem item, Member member, String notes) {
        this.instrumentItem = Objects.requireNonNull(item, "item required");
        this.uniformItem = null;
        this.member = Objects.requireNonNull(member, "member required");
        this.assignedAt = LocalDate.now();
        this.active = true;
        this.notes = notes;
    }

    public static AssetAssignmentHistory forUniform(UniformItem item, Member member, String notes) {
        return new AssetAssignmentHistory(item, member, notes);
    }

    public static AssetAssignmentHistory forInstrument(InstrumentItem item, Member member, String notes) {
        return new AssetAssignmentHistory(item, member, notes);
    }

    public void markReturned(String notes) {
        this.returnedAt = LocalDate.now();
        this.active = false;
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
}
