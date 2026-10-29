package co.com.compira.api.router;
import co.com.compira.api.notification.NotificationRealtimeHandler;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.reactive.function.server.RouterFunction;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
class NotificationRealtimeRouterContextTest {
    @Test void registersConfigurationAndRoutesWithoutAllowingBeanOverrides() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setAllowBeanDefinitionOverriding(false);
            context.registerBean(NotificationRealtimeHandler.class, () -> mock(NotificationRealtimeHandler.class));
            context.register(NotificationRealtimeRouter.class);
            assertDoesNotThrow(context::refresh);
            assertNotNull(context.getBean("notificationRealtimeRoutes", RouterFunction.class));
            assertNotNull(context.getBean(NotificationRealtimeRouter.class));
        }
    }
}
