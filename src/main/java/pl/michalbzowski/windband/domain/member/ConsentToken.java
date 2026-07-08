package pl.michalbzowski.windband.domain.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "member_consent_tokens",
        uniqueConstraints = @UniqueConstraint(columnNames = {"token"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentToken {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id; // using UUID as primary key

    @Column(name = "token", nullable = false, unique = true, updatable = false)
    private UUID token;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt; // null means timeless

    protected ConsentToken(Member member) {
        this.member = Objects.requireNonNull(member, "member required");
        this.token = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public static ConsentToken create(Member member) {
        return new ConsentToken(member);
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}