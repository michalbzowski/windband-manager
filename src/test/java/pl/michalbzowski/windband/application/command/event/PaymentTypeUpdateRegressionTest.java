package pl.michalbzowski.windband.application.command.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventType;
import pl.michalbzowski.windband.domain.event.PaymentType;
import pl.michalbzowski.windband.application.query.event.EventQueryService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Regression test for issue #28: Zmiana informacji o płatności nie jest zapisana
 * 
 * The bug was that when changing an event from FREE to PAID_SPLIT or other payment types,
 * the payment type was not being saved to the database.
 * 
 * Before fix: paymentType remains FREE after update
 * After fix: paymentType is correctly updated and saved
 */
@SpringBootTest
@Testcontainers
class PaymentTypeUpdateRegressionTest {

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
    private EventCommandService commandService;

    @Autowired
    private EventQueryService queryService;

    @Autowired
    private BandRepository bandRepository;

    @Test
    void shouldUpdatePaymentTypeWhenChangingEventType() {
        // Create a FREE event first - rely on default band
        CreateEventCommand createCmd = new CreateEventCommand();
        createCmd.setName("Test Event");
        createCmd.setDate(LocalDate.now().plusDays(7));
        createCmd.setStartTime(LocalTime.of(19, 0));
        createCmd.setLocation("Test Location");
        createCmd.setEventType("CONCERT");
        createCmd.setPaymentType("FREE");
        
        BandEvent event = commandService.createEvent(createCmd);

        // Verify initial state is FREE
        BandEvent savedEvent = queryService.getEventById(event.getId());
        assertThat(savedEvent.getPaymentType()).isEqualTo(PaymentType.FREE);

        // Update event to PAID_SPLIT
        UpdateEventCommand updateCmd = new UpdateEventCommand();
        updateCmd.setId(event.getId());
        updateCmd.setName("Test Event");
        updateCmd.setDate(LocalDate.now().plusDays(7));
        updateCmd.setStartTime(LocalTime.of(19, 0));
        updateCmd.setLocation("Test Location");
        updateCmd.setEventType("CONCERT");
        updateCmd.setPaymentType("PAID_SPLIT");
        updateCmd.setPaymentAmount(new BigDecimal("100.00"));

        commandService.updateEvent(updateCmd);

        // Verify payment type was updated
        BandEvent updatedEvent = queryService.getEventById(event.getId());
        assertThat(updatedEvent.getPaymentType())
                .as("Payment type should be updated to PAID_SPLIT")
                .isEqualTo(PaymentType.PAID_SPLIT);
        assertThat(updatedEvent.getPaymentAmount())
                .as("Payment amount should be set to 100")
                .isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void shouldUpdateEventTypeTogetherWithPaymentType() {
        // Create a CONCERT event first
        CreateEventCommand createCmd = new CreateEventCommand();
        createCmd.setName("Type Test Event");
        createCmd.setDate(LocalDate.now().plusDays(14));
        createCmd.setStartTime(LocalTime.of(20, 0));
        createCmd.setLocation("Venue");
        createCmd.setEventType("CONCERT");
        createCmd.setPaymentType("FREE");
        
        BandEvent event = commandService.createEvent(createCmd);

        // Verify initial event type
        BandEvent savedEvent = queryService.getEventById(event.getId());
        assertThat(savedEvent.getEventType()).isEqualTo(EventType.CONCERT);

        // Update to FESTIVAL with PAID_TO_TEAM
        UpdateEventCommand updateCmd = new UpdateEventCommand();
        updateCmd.setId(event.getId());
        updateCmd.setName("Type Test Event");
        updateCmd.setDate(LocalDate.now().plusDays(14));
        updateCmd.setStartTime(LocalTime.of(20, 0));
        updateCmd.setLocation("Venue");
        updateCmd.setEventType("FESTIVAL");
        updateCmd.setPaymentType("PAID_TO_TEAM");
        updateCmd.setPaymentAmount(new BigDecimal("50.00"));

        commandService.updateEvent(updateCmd);

        // Verify both event type and payment type were updated
        BandEvent updatedEvent = queryService.getEventById(event.getId());
        assertThat(updatedEvent.getEventType())
                .as("Event type should be updated to FESTIVAL")
                .isEqualTo(EventType.FESTIVAL);
        assertThat(updatedEvent.getPaymentType())
                .as("Payment type should be updated to PAID_TO_TEAM")
                .isEqualTo(PaymentType.PAID_TO_TEAM);
    }

    @Test
    void shouldClearPaymentAmountWhenChangingToFree() {
        // Create a PAID_SPLIT event first
        CreateEventCommand createCmd = new CreateEventCommand();
        createCmd.setName("Clear Test Event");
        createCmd.setDate(LocalDate.now().plusDays(21));
        createCmd.setStartTime(LocalTime.of(18, 0));
        createCmd.setLocation("Club");
        createCmd.setEventType("CONCERT");
        createCmd.setPaymentType("PAID_SPLIT");
        createCmd.setPaymentAmount(new BigDecimal("200.00"));
        
        BandEvent event = commandService.createEvent(createCmd);

        // Verify initial payment info
        BandEvent savedEvent = queryService.getEventById(event.getId());
        assertThat(savedEvent.getPaymentType()).isEqualTo(PaymentType.PAID_SPLIT);
        assertThat(savedEvent.getPaymentAmount()).isEqualTo(new BigDecimal("200.00"));

        // Update to FREE
        UpdateEventCommand updateCmd = new UpdateEventCommand();
        updateCmd.setId(event.getId());
        updateCmd.setName("Clear Test Event");
        updateCmd.setDate(LocalDate.now().plusDays(21));
        updateCmd.setStartTime(LocalTime.of(18, 0));
        updateCmd.setLocation("Club");
        updateCmd.setEventType("CONCERT");
        updateCmd.setPaymentType("FREE");

        commandService.updateEvent(updateCmd);

        // Verify payment type changed to FREE and amount is cleared
        BandEvent updatedEvent = queryService.getEventById(event.getId());
        assertThat(updatedEvent.getPaymentType())
                .as("Payment type should be updated to FREE")
                .isEqualTo(PaymentType.FREE);
        assertThat(updatedEvent.getPaymentAmount())
                .as("Payment amount should be cleared when changing to FREE")
                .isNull();
    }
}