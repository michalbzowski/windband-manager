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

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    private String description;

    @Column(nullable = false)
    private LocalDate createdAt;

    private Band(String name, String slug) {
        this.name = Objects.requireNonNull(name, "band name required");
        this.slug = Objects.requireNonNull(slug, "band slug required");
        this.createdAt = LocalDate.now();
    }

    public static Band create(String name, String slug) {
        return new Band(name, slug);
    }

    public void update(String name, String slug, String description) {
        this.name = Objects.requireNonNull(name, "band name required");
        this.slug = Objects.requireNonNull(slug, "band slug required");
        this.description = description;
    }
}
