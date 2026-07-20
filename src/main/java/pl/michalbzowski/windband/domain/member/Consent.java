package pl.michalbzowski.windband.domain.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "member_consents",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "consent_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConsentType consentType;

    @Column(nullable = false)
    private boolean granted;

    @Column(name = "granted_at")
    private Instant grantedAt;

    protected Consent(Member member, ConsentType consentType) {
        this.member = Objects.requireNonNull(member, "member required");
        this.consentType = Objects.requireNonNull(consentType, "consentType required");
        this.granted = false;
    }

    public static Consent create(Member member, ConsentType consentType) {
        return new Consent(member, consentType);
    }

    public void grant() {
        this.granted = true;
        this.grantedAt = Instant.now();
    }

    public void deny() {
        this.granted = false;
        this.grantedAt = null;
    }
}
