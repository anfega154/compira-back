package co.com.compira;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "notifications.realtime.enabled=true", "spring.main.allow-bean-definition-overriding=false",
    "notifications.realtime.url=wss://realtime.fixture.test/qa", "notifications.realtime.table=fixture",
    "notifications.realtime.queue-url=https://sqs.us-east-1.amazonaws.com/000000000000/fixture"
})
class ApplicationStartupTest {
    @Container static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17-alpine")
        .withEnv("POSTGRES_HOST_AUTH_METHOD", "trust");
    @Value("${local.server.port}") int port;
    @Autowired ApplicationContext context;
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", DATABASE::getHost);
        registry.add("DB_PORT", DATABASE::getFirstMappedPort);
        registry.add("DB_NAME", DATABASE::getDatabaseName);
        registry.add("DB_SCHEMA", () -> "public");
        registry.add("DB_USER", DATABASE::getUsername);
        registry.add("DB_PASSWORD", DATABASE::getPassword);
        registry.add("DB_SSL_MODE", () -> "disable");
        registry.add("LIQUIBASE_ENABLED", () -> "true");
        registry.add("COGNITO_REGION", () -> "us-east-1");
        registry.add("COGNITO_USER_POOL_ID", () -> "us-east-1_fixture");
        registry.add("COGNITO_CLIENT_ID", () -> "fixture-client");
    }
    @Test void startsWholeApplicationWithMigrationsRoutesAndSecurityWithoutBeanOverrides() throws Exception {
        assertNotNull(context.getBean("notificationRealtimeRoutes"));
        assertNotNull(context.getBean("notificationOutboxScheduler"));
        assertTrue(context.getBean(co.com.compira.model.notification.gateways.NotificationRealtimeGateway.class).enabled());
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var health = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode());
            assertTrue(health.body().contains("UP"));
            // OpenAPI is checked independently by the deployment audit. Its lazy
            // classpath scan performs file I/O blocked by the test-only BlockHound agent.
            var ticket = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/notifications/realtime-ticket"))
                .timeout(Duration.ofSeconds(15)).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, ticket.statusCode());
        }
    }
}
