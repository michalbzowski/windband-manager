package pl.michalbzowski.windband.application.command.rehearsal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.michalbzowski.windband.application.query.rehearsal.RehearsalQueryService;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Regression test for GitHub Issue #6:
 * "Brak możliwości usunięcia prób"
 *
 * Verifies that a scheduled rehearsal can be deleted via the API.
 */
@SpringBootTest
@Testcontainers
@Transactional
class RehearsalDeletionRegressionTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("windband_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private RehearsalCommandService commandService;

    @Autowired
    private RehearsalQueryService queryService;

    @Test
    void shouldDeleteRehearsal() {
        // Schedule a rehearsal first
        ScheduleRehearsalCommand createCmd = new ScheduleRehearsalCommand();
        createCmd.setDate(LocalDate.now().plusDays(10));
        createCmd.setStartTime(LocalTime.of(18, 0));
        createCmd.setEndTime(LocalTime.of(20, 0));
        createCmd.setLocation("Do usunięcia");

        Rehearsal rehearsal = commandService.scheduleRehearsal(createCmd);
        Long id = rehearsal.getId();
        assertThat(id).isNotNull();

        // Verify it exists
        assertThat(queryService.getRehearsalById(id)).isNotNull();

        // Delete the rehearsal
        commandService.deleteRehearsal(id);

        // Verify it no longer exists
        assertThatExceptionOfType(RehearsalNotFoundException.class)
                .isThrownBy(() -> queryService.getRehearsalById(id));
    }

    @Test
    void shouldThrowWhenDeletingNonexistentRehearsal() {
        assertThatExceptionOfType(RehearsalNotFoundException.class)
                .isThrownBy(() -> commandService.deleteRehearsal(99999L));
    }
}
