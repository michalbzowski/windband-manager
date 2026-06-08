package pl.michalbzowski.windband.application.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class AssignmentHistoryDtoTest {

    @Test
    void shouldCreateDtoWithAllFields() {
        LocalDate now = LocalDate.now();
        AssignmentHistoryDto dto = new AssignmentHistoryDto(
                1L, "Czapka", "UNIFORM", 10L,
                "Jan Kowalski", "Admin User",
                now, null, true,
                "nowy, dobry stan", null,
                null
        );

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.itemName()).isEqualTo("Czapka");
        assertThat(dto.itemType()).isEqualTo("UNIFORM");
        assertThat(dto.itemId()).isEqualTo(10L);
        assertThat(dto.memberName()).isEqualTo("Jan Kowalski");
        assertThat(dto.assignedByName()).isEqualTo("Admin User");
        assertThat(dto.assignedAt()).isEqualTo(now);
        assertThat(dto.returnedAt()).isNull();
        assertThat(dto.active()).isTrue();
        assertThat(dto.conditionAtAssign()).isEqualTo("nowy, dobry stan");
        assertThat(dto.conditionAtReturn()).isNull();
        assertThat(dto.notes()).isNull();
    }

    @Test
    void shouldCreateDtoForReturnedItem() {
        LocalDate assigned = LocalDate.of(2025, 1, 15);
        LocalDate returned = LocalDate.of(2025, 6, 20);

        AssignmentHistoryDto dto = new AssignmentHistoryDto(
                2L, "Trąbka", "INSTRUMENT", 20L,
                "Piotr Nowak", "Admin User",
                assigned, returned, false,
                "dobry", "lekko zużyty",
                "Zwrócono po sezonie"
        );

        assertThat(dto.active()).isFalse();
        assertThat(dto.assignedAt()).isEqualTo(assigned);
        assertThat(dto.returnedAt()).isEqualTo(returned);
        assertThat(dto.conditionAtAssign()).isEqualTo("dobry");
        assertThat(dto.conditionAtReturn()).isEqualTo("lekko zużyty");
        assertThat(dto.notes()).isEqualTo("Zwrócono po sezonie");
    }

    @Test
    void shouldHandleNullAssignedBy() {
        AssignmentHistoryDto dto = new AssignmentHistoryDto(
                3L, "Czapka", "UNIFORM", 10L,
                "Jan Kowalski", null,
                LocalDate.now(), null, true,
                null, null, null
        );

        assertThat(dto.assignedByName()).isNull();
        assertThat(dto.conditionAtAssign()).isNull();
    }
}
