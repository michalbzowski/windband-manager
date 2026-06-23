package pl.michalbzowski.windband.domain.dashboard;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a Superset dashboard synced to windband-manager.
 * Admins can assign dashboards to bands; users see only their band's dashboards.
 */
@Entity
@Table(name = "superset_dashboards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupersetDashboard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "superset_id", nullable = false, unique = true)
    private Integer supersetId;

    @Column(name = "superset_uuid", nullable = false, unique = true, length = 36)
    private String supersetUuid;

    @Column(name = "embedded_uuid", length = 36)
    private String embeddedUuid;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String slug;

    @Column(length = 1000)
    private String description;

    @Column(length = 64)
    private String icon;

    @Column(nullable = false)
    private Integer position;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "is_embedded", nullable = false)
    private boolean embedded;

    @Column(name = "first_synced_at", nullable = false)
    private LocalDateTime firstSyncedAt;

    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    public SupersetDashboard(Integer supersetId, String supersetUuid, String title, String slug) {
        this.supersetId = Objects.requireNonNull(supersetId, "supersetId required");
        this.supersetUuid = Objects.requireNonNull(supersetUuid, "supersetUuid required");
        this.title = Objects.requireNonNull(title, "title required");
        this.slug = Objects.requireNonNull(slug, "slug required");
        this.position = 0;
        this.active = true;
        this.embedded = true;
        this.firstSyncedAt = LocalDateTime.now();
        this.lastSyncedAt = LocalDateTime.now();
    }

    public void updateFromSuperset(String supersetUuid, String title, String slug, String description) {
        this.supersetUuid = Objects.requireNonNull(supersetUuid, "supersetUuid required");
        this.title = Objects.requireNonNull(title, "title required");
        this.slug = Objects.requireNonNull(slug, "slug required");
        this.description = description;
        this.lastSyncedAt = LocalDateTime.now();
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setEmbedded(boolean embedded) {
        this.embedded = embedded;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getEmbeddedUuid() {
        return embeddedUuid;
    }

    public void setEmbeddedUuid(String embeddedUuid) {
        this.embeddedUuid = embeddedUuid;
    }
}
