package pl.michalbzowski.windband.domain.inventory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "award_attribute_values",
        uniqueConstraints = @UniqueConstraint(columnNames = {"award_item_id", "attribute_def_id"}))
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AwardAttributeValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "award_item_id", nullable = false)
    private AwardItem awardItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_def_id", nullable = false)
    private AwardAttributeDef attributeDef;

    @Column(name = "value_text")
    private String value;

    private AwardAttributeValue(AwardItem item, AwardAttributeDef def, String value) {
        this.awardItem = Objects.requireNonNull(item);
        this.attributeDef = Objects.requireNonNull(def);
        this.value = value;
    }

    public static AwardAttributeValue create(AwardItem item, AwardAttributeDef def, String value) {
        return new AwardAttributeValue(item, def, value);
    }

    public void setValue(String value) {
        this.value = value;
    }
}
