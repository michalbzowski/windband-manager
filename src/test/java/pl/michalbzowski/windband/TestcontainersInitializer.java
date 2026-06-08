package pl.michalbzowski.windband;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;

import java.util.stream.Stream;

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
 */
public class TestcontainersInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("windband_test")
            .withUsername("test")
            .withPassword("test");

    static {
        // Only start if not already running (singleton in this JVM)
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
    }

    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
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