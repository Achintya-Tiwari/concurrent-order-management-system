package com.psl.oms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * OmsApplicationTest – smoke test that verifies the Spring context loads
 * without errors.
 *
 * <p>{@code @SpringBootTest} starts the full application context.
 * If any bean definition is broken, an auto-configuration is missing, or
 * a @Value property is unresolvable, this test fails immediately — giving
 * fast feedback before running unit tests.
 *
 * <p>Uses the "test" profile to connect to H2 instead of MySQL.
 */
@SpringBootTest
@ActiveProfiles("test")
class OmsApplicationTest {

    @Test
    @DisplayName("Spring application context loads successfully")
    void contextLoads() {
        // If the context fails to start, this test fails automatically.
        // No assertions needed — the @SpringBootTest does the heavy lifting.
    }
}
