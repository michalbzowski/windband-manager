package pl.michalbzowski.windband.domain.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GroupMember> members = new ArrayList<>();

    public Group(String name, String description) {
        this.name = name;
        this.description = description;
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
}
