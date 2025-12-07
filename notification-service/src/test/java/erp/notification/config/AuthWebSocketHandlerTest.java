package erp.notification.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;
import erp.common.security.AuthenticatedUser;
import erp.common.security.JwtAuthenticator;
import erp.common.security.Role;
import erp.notification.config.WebSocketConfig.SessionStore;

@DisplayName("AuthWebSocketHandler 단위 테스트")
class AuthWebSocketHandlerTest {

    private JwtAuthenticator jwtAuthenticator;
    private SessionStore sessionStore;

    @BeforeEach
    void setUp() {
        jwtAuthenticator = mock(JwtAuthenticator.class);
        sessionStore = mock(SessionStore.class);
    }

    @Test
    @DisplayName("QueryString 토큰을 인증하고 세션을 등록한다")
    void authenticateAndStoreSession() throws Exception {
        // given
        WebSocketSession session = mockSessionWithToken("jwt-token");
        AuthenticatedUser user = new AuthenticatedUser(1L, List.of(Role.EMPLOYEE));
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(user, null);
        authentication.setAuthenticated(true);
        when(jwtAuthenticator.authenticate("jwt-token")).thenReturn(authentication);

        TextWebSocketHandler handler = createHandler();

        // when
        handler.afterConnectionEstablished(session);

        // then
        verify(sessionStore).put(eq(1L), eq(session));
    }

    @Test
    @DisplayName("token 이 없으면 AUTH_TOKEN_MISSING 예외를 던진다")
    void throwWhenTokenMissing() throws Exception {
        // given
        WebSocketSession session = mockSessionWithToken(null);
        TextWebSocketHandler handler = createHandler();

        // when & then: 예외 검증
        assertThatThrownBy(() -> handler.afterConnectionEstablished(session))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_TOKEN_MISSING);
    }

    @Test
    @DisplayName("연결 종료 시 SessionStore 에서 제거한다")
    void removeSessionOnClose() throws Exception {
        // given
        WebSocketSession session = mock(WebSocketSession.class);
        TextWebSocketHandler handler = createHandler();

        // when
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        // then
        verify(sessionStore).remove(eq(session));
    }

    private TextWebSocketHandler createHandler() throws Exception {
        Class<?> clazz = Class.forName("erp.notification.config.WebSocketConfig$AuthWebSocketHandler");
        Constructor<?> constructor = clazz.getDeclaredConstructor(JwtAuthenticator.class, SessionStore.class);
        constructor.setAccessible(true);
        return (TextWebSocketHandler) constructor.newInstance(jwtAuthenticator, sessionStore);
    }

    private WebSocketSession mockSessionWithToken(String token) {
        WebSocketSession session = mock(WebSocketSession.class);
        if (token != null) {
            when(session.getUri()).thenReturn(URI.create("ws://localhost/ws?token=" + token));
        } else {
            when(session.getUri()).thenReturn(URI.create("ws://localhost/ws"));
        }
        return session;
    }
}
