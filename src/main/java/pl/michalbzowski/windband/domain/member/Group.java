package pl.michalbzowski.windband.domain.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;

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
        return dynamicSource != null;
    }

    public static Group createDynamic(String name, Band band, MemberAttributeDef source) {
        Group g = new Group(name, "Grupa dynamiczna na podstawie atrybutu " + name, band);
        g.dynamicSource = source;
        return g;
    }
}
