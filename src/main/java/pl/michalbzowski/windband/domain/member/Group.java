package pl.michalbzowski.windband.domain.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.band.MemberAttributeValueRepository;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "member_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "band_id", nullable = false)
    private Band band;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dynamic_source_id", unique = true)
    private MemberAttributeDef dynamicSource;

    /**
     * Discriminator for the dynamic source kind. Null for a manual group OR for a
     * legacy attribute-backed group whose source is expressed via {@link #dynamicSource}
     * (those are treated as {@code ATTRIBUTE}). Non-null only for {@code MEMBER_FIELD}
     * sources (e.g. the fixed {@code active} field).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "dynamic_source_type", nullable = true)
    private DynamicSourceType dynamicSourceType;

    /**
     * Source reference key: attribute def id (as string) for {@code ATTRIBUTE},
     * or the field name (e.g. "active") for {@code MEMBER_FIELD}.
     */
    @Column(name = "dynamic_source_key", nullable = true)
    private String dynamicSourceKey;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GroupMember> members = new ArrayList<>();

    public Group(String name, String description, Band band) {
        this.name = name;
        this.description = description;
        this.band = band;
    }

    public void addMember(Member member) {
        boolean alreadyInGroup = members.stream()
                .anyMatch(gm -> gm.getMember().equals(member));
        if (alreadyInGroup) {
            throw new IllegalStateException("Member already in group: " + member.getId());
        }
        members.add(new GroupMember(this, member));
    }

    public void removeMember(Member member) {
        members.removeIf(gm -> gm.getMember().equals(member));
    }

    public int getMemberCount() {
        return members.size();
    }

    public boolean isDynamic() {
        return dynamicSource != null || dynamicSourceType != null;
    }

    public static Group createDynamic(String name, Band band, MemberAttributeDef source) {
        Group g = new Group(name, "Grupa dynamiczna na podstawie atrybutu " + name, band);
        g.dynamicSource = source;
        return g;
    }

    /**
     * Create a dynamic group backed by a fixed member field (e.g. "active").
     * The resulting group is auto-managed, exactly like an attribute-backed one.
     */
    public static Group createDynamicForMemberField(String field, Band band) {
        Group g = new Group("Aktywni", "Grupa dynamiczna na podstawie pola członka: " + field, band);
        g.dynamicSourceType = DynamicSourceType.MEMBER_FIELD;
        g.dynamicSourceKey = field;
        return g;
    }

    /**
     * Resolve the polymorphic source of this dynamic group. For attribute-backed
     * groups the value repository is consulted to read per-member attribute values;
     * for member-field-backed groups the field name drives evaluation directly.
     *
     * @throws IllegalStateException if the group is not dynamic.
     */
    public DynamicGroupSource resolveSource(MemberAttributeValueRepository valueRepository) {
        if (dynamicSource != null) {
            return new AttributeDefSource(dynamicSource, valueRepository);
        }
        if (dynamicSourceType == DynamicSourceType.MEMBER_FIELD) {
            return new MemberFieldSource(dynamicSourceKey);
        }
        throw new IllegalStateException("Group is not dynamic: " + id);
    }

    /** Public: driven by GroupCommandService for dynamic-group rename (cross-package call). */
    public void renameForDynamicSource(String newName) {
        this.name = newName;
        this.description = "Grupa dynamiczna na podstawie atrybutu " + newName;
    }
}
