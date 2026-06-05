package pl.michalbzowski.windband.domain.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.Objects;

@Entity
@Table(name = "instruments", uniqueConstraints = {
        @UniqueConstraint(columnNames = "name")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "sort_priority")
    private Integer sortPriority = 0;

    private Instrument(String name) {
        this.name = Objects.requireNonNull(name, "instrument name required");
    }

    public static Instrument create(String name) {
        return new Instrument(name);
    }

    public void updateName(String name) {
        this.name = Objects.requireNonNull(name, "instrument name required");
    }

    public void updateDescription(String description) {
        this.description = description;
    }

    public void updateSortPriority(Integer sortPriority) {
        this.sortPriority = sortPriority != null ? sortPriority : 0;
    }
}
