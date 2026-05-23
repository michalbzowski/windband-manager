package pl.michalbzowski.windband;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApplicationStartupTest {

    @Test
    void contextLoads() {
        // If Spring context loads successfully, the test passes
    }
}
