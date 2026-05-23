package pl.michalbzowski.windband.domain.band;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "bands")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Band {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(nullable = false)
    private LocalDate createdAt;

    private Band(String name) {
        this.name = Objects.requireNonNull(name, "band name required");
        this.createdAt = LocalDate.now();
    }

    public static Band create(String name) {
        return new Band(name);
    }

    public void update(String name, String description) {
        this.name = Objects.requireNonNull(name, "band name required");
        this.description = description;
    }
}
