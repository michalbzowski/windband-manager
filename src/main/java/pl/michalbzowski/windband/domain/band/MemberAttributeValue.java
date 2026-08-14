package pl.michalbzowski.windband.domain.band;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.Objects;

@Entity
@Table(name = "member_attribute_values",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "attribute_def_id"}))
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAttributeValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_def_id", nullable = false)
    private MemberAttributeDef attributeDef;

    @Column(name = "value_text")
    private String value;

    private MemberAttributeValue(Member member, MemberAttributeDef attributeDef, String value) {
        this.member = Objects.requireNonNull(member, "member required");
        this.attributeDef = Objects.requireNonNull(attributeDef, "attributeDef required");
        this.value = value;
    }

    public static MemberAttributeValue create(Member member, MemberAttributeDef attributeDef, String value) {
        return new MemberAttributeValue(member, attributeDef, value);
    }

    public void setValue(String value) {
        this.value = value;
    }
}
