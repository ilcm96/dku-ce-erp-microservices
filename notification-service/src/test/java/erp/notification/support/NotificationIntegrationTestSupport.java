package erp.notification.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import erp.notification.config.WebSocketConfig.SessionStore;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class NotificationIntegrationTestSupport {

    @LocalServerPort
    protected int port;

    @Autowired
    protected SessionStore sessionStore;

    protected String wsUrl(String token) {
        return "ws://localhost:" + port + "/ws?token=" + token;
    }
}
