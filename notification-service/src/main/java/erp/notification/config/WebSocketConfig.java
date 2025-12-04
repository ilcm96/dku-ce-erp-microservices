package erp.notification.config;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;
import erp.common.security.AuthenticatedUser;
import erp.common.security.JwtAuthenticator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final JwtAuthenticator jwtAuthenticator;
    private final SessionStore sessionStore;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new AuthWebSocketHandler(jwtAuthenticator, sessionStore), "/ws")
                .setAllowedOrigins("*")
                .addInterceptors(new HttpSessionHandshakeInterceptor());
    }

    @Slf4j
    private static class AuthWebSocketHandler extends TextWebSocketHandler {
        private final JwtAuthenticator jwtAuthenticator;
        private final SessionStore sessionStore;

        AuthWebSocketHandler(JwtAuthenticator jwtAuthenticator, SessionStore sessionStore) {
            this.jwtAuthenticator = jwtAuthenticator;
            this.sessionStore = sessionStore;
        }

        @Override
        public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
            String token = extractToken(session);
            AuthenticatedUser user =
                    (AuthenticatedUser) jwtAuthenticator.authenticate(token).getPrincipal();
            sessionStore.put(user.userId(), session);
            log.info("WebSocket connected userId={}", user.userId());
        }

        @Override
        public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status)
                throws Exception {
            sessionStore.remove(session);
        }

        @Override
        protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message)
                throws Exception {
            // 서버 발신 전용으로 사용; 클라이언트 메시지는 현재 무시
        }

        private String extractToken(WebSocketSession session) {
            URI uri = session.getUri();
            String token = uri == null ? null
                    : UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("token");
            if (token == null || token.isBlank()) {
                throw new CustomException(ErrorCode.AUTH_TOKEN_MISSING);
            }
            return token;
        }
    }

    @Component
    public static class SessionStore {
        private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

        public void put(Long userId, WebSocketSession session) {
            sessions.put(userId, session);
        }

        public void remove(WebSocketSession session) {
            sessions.entrySet().removeIf(entry -> entry.getValue().equals(session));
        }

        public void sendTo(Long userId, String payload) {
            WebSocketSession session = sessions.get(userId);
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(payload));
                } catch (Exception ignored) {
                }
            }
        }
    }
}
