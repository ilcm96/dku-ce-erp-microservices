package erp.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@DisplayName("RealJwtAuthenticator")
class RealJwtAuthenticatorTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef01234567";

    @Nested
    @DisplayName("authenticate")
    class Authenticate {

        @Test
        void roles_리스트_토큰을_인증하면_사용자정보를_반환한다() {
            // given
            String token = Jwts.builder()
                    .subject("30")
                    .claim("roles", List.of("EMPLOYEE", "ADMIN"))
                    .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                    .compact();
            RealJwtAuthenticator authenticator = new RealJwtAuthenticator(SECRET);

            // when
            Authentication authentication = authenticator.authenticate(token);

            // then: 반환값 검증
            AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
            assertThat(principal.userId()).isEqualTo(30L);
            assertThat(principal.roles()).containsExactly(Role.EMPLOYEE, Role.ADMIN);
            assertThat(authentication.getAuthorities())
                    .extracting("authority")
                    .containsExactlyInAnyOrder("ROLE_EMPLOYEE", "ROLE_ADMIN");
        }

        @Test
        void roles_단일_문자열_토큰도_매핑된다() {
            // given
            String token = Jwts.builder()
                    .subject("40")
                    .claim("roles", "admin")
                    .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                    .compact();
            RealJwtAuthenticator authenticator = new RealJwtAuthenticator(SECRET);

            // when
            Authentication authentication = authenticator.authenticate(token);

            // then: 반환값 검증
            AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
            assertThat(principal.userId()).isEqualTo(40L);
            assertThat(principal.roles()).containsExactly(Role.ADMIN);
        }

        @Test
        void roles_누락시_EMPLOYEE_기본값을_사용한다() {
            // given
            String token = Jwts.builder()
                    .subject("50")
                    .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                    .compact();
            RealJwtAuthenticator authenticator = new RealJwtAuthenticator(SECRET);

            // when
            Authentication authentication = authenticator.authenticate(token);

            // then: 반환값 검증
            AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
            assertThat(principal.userId()).isEqualTo(50L);
            assertThat(principal.roles()).containsExactly(Role.EMPLOYEE);
        }

        @Test
        void 서명이_다르면_AUTH_INVALID_TOKEN을_던진다() {
            // given
            String forgedToken = Jwts.builder()
                    .subject("60")
                    .claim("roles", List.of("EMPLOYEE"))
                    .signWith(Keys.hmacShaKeyFor("another-secret-key-that-differs-0001".getBytes(StandardCharsets.UTF_8)))
                    .compact();
            RealJwtAuthenticator authenticator = new RealJwtAuthenticator(SECRET);

            // when & then: 예외 검증
            assertThatThrownBy(() -> authenticator.authenticate(forgedToken))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
        }
    }
}
