package erp.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import erp.notification.config.WebSocketConfig.SessionStore;
import erp.notification.support.NotificationIntegrationTestSupport;
import erp.notification.support.TestJwtFactory;
import erp.common.security.Role;

@DisplayName("WebSocket 통합 테스트")
class NotificationWebSocketIntegrationTest extends NotificationIntegrationTestSupport {

    private StandardWebSocketClient client;

    @BeforeEach
    void setUp() throws Exception {
        client = new StandardWebSocketClient();
        sessions().clear();
    }

    @Test
    @DisplayName("JWT 토큰으로 접속하면 세션이 등록되고 메시지를 수신한다")
    void connectWithJwtToken() throws Exception {
        // given
        BlockingQueue<String> messages = new ArrayBlockingQueue<>(1);
        CountDownLatch connected = new CountDownLatch(1);

        WebSocketHandler handler = new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                connected.countDown();
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                messages.offer(message.getPayload());
            }
        };

        String token = TestJwtFactory.createToken(1L, List.of(Role.EMPLOYEE));
        CompletableFuture<WebSocketSession> future = client.execute(handler, wsUrl(token));
        WebSocketSession clientSession = future.get(3, TimeUnit.SECONDS);

        // when
        assertThat(connected.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(waitForSession(1L)).isTrue();
        sessionStore.sendTo(1L, "hello");

        // then
        assertThat(messages.poll(3, TimeUnit.SECONDS)).isEqualTo("hello");

        clientSession.close();
    }

    @Test
    @DisplayName("token 없이 접속하면 AUTH_TOKEN_MISSING 으로 실패한다")
    void failWhenTokenMissing() throws Exception {
        // given
        CountDownLatch closed = new CountDownLatch(1);
        AtomicReference<CloseStatus> closeStatus = new AtomicReference<>();

        WebSocketHandler handler = new TextWebSocketHandler() {
            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                closeStatus.set(status);
                closed.countDown();
            }
        };

        CompletableFuture<WebSocketSession> future =
                client.execute(handler, "ws://localhost:" + port + "/ws");

        // when & then: 예외 검증
        WebSocketSession session = future.get(3, TimeUnit.SECONDS);
        closed.await(2, TimeUnit.SECONDS);

        assertThat(session.isOpen()).isFalse();
        assertThat(closeStatus.get()).isNotNull();
        assertThat(closeStatus.get().getCode()).isEqualTo(CloseStatus.SERVER_ERROR.getCode());
        assertThat(sessions()).isEmpty();
    }

    private boolean waitForSession(long userId) throws Exception {
        for (int i = 0; i < 20; i++) {
            if (sessions().containsKey(userId)) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, WebSocketSession> sessions() throws Exception {
        Field field = SessionStore.class.getDeclaredField("sessions");
        field.setAccessible(true);
        return (Map<Long, WebSocketSession>) field.get(sessionStore);
    }
}
