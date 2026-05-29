package pl.michalbzowski.windband.domain.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.band.Band;

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

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private LocalDate joinedDate;

    private LocalDate resignedDate;

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MemberInstrument> instruments = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "band_id", nullable = false)
    private Band band;

    private Member(String firstName, String lastName, LocalDate dateOfBirth, Band band) {
        this.firstName = Objects.requireNonNull(firstName, "firstName required");
        this.lastName = Objects.requireNonNull(lastName, "lastName required");
        this.dateOfBirth = Objects.requireNonNull(dateOfBirth, "dateOfBirth required");
        this.active = true;
        this.joinedDate = LocalDate.now();
        this.band = Objects.requireNonNull(band, "band required");
    }

    public static Member create(String firstName, String lastName, LocalDate dateOfBirth, Band band) {
        return new Member(firstName, lastName, dateOfBirth, band);
    }

    public void updateContact(String email, String phone) {
        this.email = email;
        this.phone = phone;
    }

    public void update(String firstName, String lastName, LocalDate dateOfBirth, boolean active) {
        this.firstName = Objects.requireNonNull(firstName, "firstName required");
        this.lastName = Objects.requireNonNull(lastName, "lastName required");
        this.dateOfBirth = Objects.requireNonNull(dateOfBirth, "dateOfBirth required");
        this.active = active;
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

    public void changeInstrument(Instrument newInstrument) {
        // Remove all existing instrument assignments (orphanRemoval handles DB delete)
        List<MemberInstrument> toRemove = new ArrayList<>(instruments);
        for (MemberInstrument mi : toRemove) {
            instruments.remove(mi);
        }
        instruments.add(new MemberInstrument(this, newInstrument, true));
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

    public void deactivate() {
        this.active = false;
        this.resignedDate = LocalDate.now();
    }

    public void setJoinedDate(LocalDate joinedDate) {
        this.joinedDate = joinedDate;
    }

    public void setResignedDate(LocalDate resignedDate) {
        this.resignedDate = resignedDate;
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
