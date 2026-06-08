package pl.michalbzowski.windband;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Stricter base class for integration tests that verify Flyway-managed schema.
 * Extends {@link BaseIntegrationTest} which provides a shared PostgreSQL
 * container, the test profile, and local mail defaults.
 *
 * <p>Adds Flyway + ddl-auto=validate so schema changes must go through
 * Flyway migrations (no auto-create from JPA entities).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
public abstract class IntegrationTest extends BaseIntegrationTest {
}