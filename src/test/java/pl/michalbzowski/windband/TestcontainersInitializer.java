package pl.michalbzowski.windband;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * ApplicationContextInitializer that starts a shared PostgreSQL container
 * and injects datasource + mail env properties into the Spring Environment.
 *
 * <p>Use with {@code @ContextConfiguration(initializers = TestcontainersInitializer.class)}
 * on test classes. Unlike {@code @DynamicPropertySource}, this approach IS
 * inherited by subclasses — perfect for a base class.</p>
 *
 * <p>The container is started once and reused across all test classes using
 * this initializer, as long as they share the same JVM.</p>
 *
 * <p><strong>Graceful fallback:</strong> If Docker is not available (e.g. on a
 * developer machine without Docker running), the initializer logs a warning
 * and does NOT override the datasource properties. The test profile's H2
 * configuration (from {@code application-test.yml}) is used instead, so
 * tests can still run without Docker.</p>
 */
public class TestcontainersInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(TestcontainersInitializer.class);

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("windband_test")
            .withUsername("test")
            .withPassword("test");

    private static final boolean DOCKER_AVAILABLE;

    static {
        boolean available = false;
        try {
            if (!POSTGRES.isRunning()) {
                POSTGRES.start();
            }
            available = true;
            log.info("Testcontainers: PostgreSQL container started at {}", POSTGRES.getJdbcUrl());
        } catch (Exception e) {
            log.warn("Testcontainers: Docker not available ({}). Falling back to H2.", e.getMessage());
        }
        DOCKER_AVAILABLE = available;
    }

    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        if (!DOCKER_AVAILABLE) {
            // Still provide mail defaults so application.yml resolves ${MAIL_HOST} etc.
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    ctx,
                    "MAIL_HOST=localhost",
                    "MAIL_PORT=1025",
                    "MAIL_USER=test",
                    "MAIL_PASS=test"
            );
            return;
        }

        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                ctx,
                // Database connection from Testcontainers
                "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "spring.datasource.username=" + POSTGRES.getUsername(),
                "spring.datasource.password=" + POSTGRES.getPassword(),
                "spring.datasource.driver-class-name=org.postgresql.Driver",
                // Override H2 dialect from test profile — actual DB is PostgreSQL
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
                // Provide local defaults for mail env vars so application.yml resolves
                "MAIL_HOST=localhost",
                "MAIL_PORT=1025",
                "MAIL_USER=test",
                "MAIL_PASS=test"
        );
    }
}
