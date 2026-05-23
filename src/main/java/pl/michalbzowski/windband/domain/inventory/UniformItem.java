package pl.michalbzowski.windband.domain.inventory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;import pl.michalbzowski.windband.domain.member.Member;

import java.util.Objects;

@Entity
@Table(name = "uniform_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UniformItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member assignedMember;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OwnershipStatus ownershipStatus;

    private UniformItem(String name, OwnershipStatus ownershipStatus) {
        this.name = Objects.requireNonNull(name, "name required");
        this.ownershipStatus = Objects.requireNonNull(ownershipStatus, "ownershipStatus required");
    }

    public static UniformItem createOwned(String name) {
        return new UniformItem(name, OwnershipStatus.OWNED);
    }

    public static UniformItem createBorrowed(String name) {
        return new UniformItem(name, OwnershipStatus.BORROWED);
    }

    public static UniformItem createMissing(String name) {
        return new UniformItem(name, OwnershipStatus.MISSING);
    }

    public void assignTo(Member member) {
        this.assignedMember = member;
    }

    public void unassign() {
        this.assignedMember = null;
    }

    public void updateOwnershipStatus(OwnershipStatus status) {
        this.ownershipStatus = Objects.requireNonNull(status);
    }
}
