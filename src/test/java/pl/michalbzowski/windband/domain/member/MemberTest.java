package pl.michalbzowski.windband.domain.member;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class MemberTest {

    @Test
    void shouldCreateActiveMember() {
        Member member = Member.create("Jan", "Kowalski",
                LocalDate.of(1990, 1, 15), MemberRole.MEMBER, true);

        assertThat(member.getFirstName()).isEqualTo("Jan");
        assertThat(member.getLastName()).isEqualTo("Kowalski");
        assertThat(member.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 15));
        assertThat(member.getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(member.isOspMember()).isTrue();
        assertThat(member.isActive()).isTrue();
        assertThat(member.getJoinedDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void shouldCreateGuest() {
        Member guest = Member.create("Piotr", "Nowak",
                LocalDate.of(1985, 6, 20), MemberRole.GUEST, false);

        assertThat(guest.getRole()).isEqualTo(MemberRole.GUEST);
        assertThat(guest.isOspMember()).isFalse();
    }

    @Test
    void shouldDetectMinor() {
        Member minor = Member.create("Adam", "Młody",
                LocalDate.now().minusYears(15), MemberRole.MEMBER, false);

        assertThat(minor.isMinor()).isTrue();
        assertThat(minor.isSenior()).isFalse();
    }

    @Test
    void shouldDetectSenior() {
        Member senior = Member.create("Jan", "Senior",
                LocalDate.of(1950, 1, 1), MemberRole.MEMBER, false);

        assertThat(senior.isSenior()).isTrue();
        assertThat(senior.isMinor()).isFalse();
    }

    @Test
    void shouldDetectAdult() {
        Member adult = Member.create("Anna", "Dorosła",
                LocalDate.of(1995, 3, 10), MemberRole.MEMBER, false);

        assertThat(adult.isMinor()).isFalse();
        assertThat(adult.isSenior()).isFalse();
    }

    @Test
    void shouldAddInstrument() {
        Member member = Member.create("Jan", "Kowalski",
                LocalDate.of(1990, 1, 15), MemberRole.MEMBER, true);
        Instrument trumpet = Instrument.create("Trąbka");

        member.addInstrument(trumpet, true);

        assertThat(member.getPrimaryInstrument()).hasValue(trumpet);
        assertThat(member.getAllInstruments()).hasSize(1);
    }

    @Test
    void shouldAddMultipleInstruments() {
        Member member = Member.create("Jan", "Kowalski",
                LocalDate.of(1990, 1, 15), MemberRole.MEMBER, true);
        Instrument trumpet = Instrument.create("Trąbka");
        Instrument flute = Instrument.create("Flet");

        member.addInstrument(trumpet, true);
        member.addInstrument(flute, false);

        assertThat(member.getPrimaryInstrument()).hasValue(trumpet);
        assertThat(member.getAllInstruments()).hasSize(2);
    }

    @Test
    void shouldThrowWhenAddingDuplicateInstrument() {
        Member member = Member.create("Jan", "Kowalski",
                LocalDate.of(1990, 1, 15), MemberRole.MEMBER, true);
        Instrument trumpet = Instrument.create("Trąbka");

        member.addInstrument(trumpet, true);

        assertThatThrownBy(() -> member.addInstrument(trumpet, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has instrument");
    }

    @Test
    void shouldDeactivate() {
        Member member = Member.create("Jan", "Kowalski",
                LocalDate.of(1990, 1, 15), MemberRole.MEMBER, true);

        member.deactivate();

        assertThat(member.isActive()).isFalse();
    }

    @Test
    void shouldThrowWhenCreatingWithNullFirstName() {
        assertThatThrownBy(() -> Member.create(null, "Kowalski",
                LocalDate.of(1990, 1, 15), MemberRole.MEMBER, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldCalculateAge() {
        Member member = Member.create("Jan", "Kowalski",
                LocalDate.of(1990, 1, 15), MemberRole.MEMBER, true);

        int expectedAge = LocalDate.now().getYear() - 1990;
        assertThat(member.getAge()).isEqualTo(expectedAge);
    }
}
