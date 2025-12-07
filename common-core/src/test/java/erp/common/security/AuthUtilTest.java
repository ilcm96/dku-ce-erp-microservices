package erp.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;

@DisplayName("AuthUtil")
class AuthUtilTest {

    private final AuthUtil authUtil = new AuthUtil();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("currentUserId")
    class CurrentUserId {

        @Test
        void 인증된_사용자의_id를_반환한다() {
            // given
            AuthenticatedUser principal = new AuthenticatedUser(1L, List.of(Role.ADMIN));
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // when
            Long result = authUtil.currentUserId();

            // then: 반환값 검증
            assertThat(result).isEqualTo(1L);
        }

        @Test
        void 인증정보가_없으면_UNAUTHORIZED_예외를_던진다() {
            // when & then: 예외 검증
            assertThatThrownBy(() -> authUtil.currentUserId())
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }

        @Test
        void principal_타입이_다르면_UNAUTHORIZED_예외를_던진다() {
            // given
            Authentication authentication = new UsernamePasswordAuthenticationToken("user", null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // when & then: 예외 검증
            assertThatThrownBy(() -> authUtil.currentUserId())
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("hasRole")
    class HasRole {

        @Test
        void 보유한_권한이면_true를_반환한다() {
            // given
            AuthenticatedUser principal = new AuthenticatedUser(1L, List.of(Role.ADMIN, Role.EMPLOYEE));
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // when
            boolean result = authUtil.hasRole(Role.ADMIN);

            // then: 반환값 검증
            assertThat(result).isTrue();
        }

        @Test
        void 보유하지_않은_권한이면_false를_반환한다() {
            // given
            AuthenticatedUser principal = new AuthenticatedUser(1L, List.of(Role.EMPLOYEE));
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // when
            boolean result = authUtil.hasRole(Role.ADMIN);

            // then: 반환값 검증
            assertThat(result).isFalse();
        }
    }
}
