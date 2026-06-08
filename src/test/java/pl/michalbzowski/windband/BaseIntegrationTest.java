package pl.michalbzowski.windband;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for tests that use Testcontainers PostgreSQL.
 * Provides:
 * <ul>
 *   <li>Shared PostgreSQL container via {@link TestcontainersInitializer}</li>
 *   <li>test profile (H2 database → PostgreSQL via initializer override)</li>
 *   <li>Local defaults for mail env vars (so {@code application.yml} resolves
 *       {@code ${MAIL_HOST}} etc.)</li>
 * </ul>
 *
 * <p>Extend this class to get a fully-configured database for integration tests
 * without needing a running PostgreSQL or Keycloak instance.
 * The PostgreSQL container is started once per JVM and reused across all
 * extending test classes.</p>
 *
 * <p>If you need a SECOND PostgreSQL container with different settings
 * (e.g. Flyway-enabled, ddl-auto: validate), extend this class and add
 * your own {@code @Container} + {@code @DynamicPropertySource} — but note
 * that {@code @DynamicPropertySource} does NOT inherit, so you must add
 * it to the concrete test class, not an intermediate base.</p>
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestcontainersInitializer.class)
public abstract class BaseIntegrationTest {
    // The container lifecycle and property injection are handled by
    // TestcontainersInitializer via @ContextConfiguration.
    // The @Testcontainers annotation enables container cleanup after tests.
}