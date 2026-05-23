package pl.michalbzowski.windband.domain.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    private String email;
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @Column(nullable = false)
    private boolean ospMember;

    @Column(nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MemberInstrument> instruments = new ArrayList<>();

    @Column(nullable = false)
    private LocalDate joinedDate;

    private Member(String firstName, String lastName, LocalDate dateOfBirth,
                   MemberRole role, boolean ospMember) {
        this.firstName = Objects.requireNonNull(firstName, "firstName required");
        this.lastName = Objects.requireNonNull(lastName, "lastName required");
        this.dateOfBirth = Objects.requireNonNull(dateOfBirth, "dateOfBirth required");
        this.role = Objects.requireNonNull(role, "role required");
        this.ospMember = ospMember;
        this.active = true;
        this.joinedDate = LocalDate.now();
    }

    public static Member create(String firstName, String lastName, LocalDate dateOfBirth,
                                MemberRole role, boolean ospMember) {
        return new Member(firstName, lastName, dateOfBirth, role, ospMember);
    }

    public void updateContact(String email, String phone) {
        this.email = email;
        this.phone = phone;
    }

    public void addInstrument(Instrument instrument, boolean isPrimary) {
        boolean alreadyHas = instruments.stream()
                .anyMatch(mi -> mi.getInstrument() == instrument);
        if (alreadyHas) {
            throw new IllegalStateException("Member already has instrument: " + instrument.getName());
        }
        instruments.add(new MemberInstrument(this, instrument, isPrimary));
    }

    public void removeInstrument(Instrument instrument) {
        instruments.removeIf(mi -> mi.getInstrument() == instrument);
    }

    public Optional<Instrument> getPrimaryInstrument() {
        return instruments.stream()
                .filter(MemberInstrument::isPrimary)
                .map(MemberInstrument::getInstrument)
                .findFirst();
    }

    public List<Instrument> getAllInstruments() {
        return instruments.stream()
                .map(MemberInstrument::getInstrument)
                .toList();
    }

    public void markAsGuest() {
        this.role = MemberRole.GUEST;
    }

    public void deactivate() {
        this.active = false;
    }

    public boolean isMinor() {
        return dateOfBirth.plusYears(18).isAfter(LocalDate.now());
    }

    public boolean isSenior() {
        return dateOfBirth.plusYears(65).isBefore(LocalDate.now());
    }

    public int getAge() {
        return LocalDate.now().getYear() - dateOfBirth.getYear();
    }
}
