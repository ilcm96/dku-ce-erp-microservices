package erp.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import erp.notification.config.WebSocketConfig.SessionStore;

@DisplayName("SessionStore 단위 테스트")
class SessionStoreTest {

    private SessionStore sessionStore;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        sessionStore = new SessionStore();
        session = mock(WebSocketSession.class);
    }

    @Test
    @DisplayName("열린 세션이면 메시지를 전송한다")
    void sendMessageWhenOpen() throws Exception {
        // given
        when(session.isOpen()).thenReturn(true);
        sessionStore.put(1L, session);

        // when
        sessionStore.sendTo(1L, "hello");

        // then
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).isEqualTo("hello");
    }

    @Test
    @DisplayName("세션 제거 후에는 메시지를 전송하지 않는다")
    void doNotSendAfterRemove() throws Exception {
        // given
        when(session.isOpen()).thenReturn(true);
        sessionStore.put(1L, session);
        sessionStore.remove(session);
        clearInvocations(session);

        // when
        sessionStore.sendTo(1L, "hello");

        // then
        verify(session, never()).sendMessage(any());
    }

    @Test
    @DisplayName("닫힌 세션이면 메시지를 전송하지 않는다")
    void skipWhenSessionClosed() throws Exception {
        // given
        when(session.isOpen()).thenReturn(false);
        sessionStore.put(1L, session);

        // when
        sessionStore.sendTo(1L, "hello");

        // then
        verify(session, never()).sendMessage(any());
    }
}
