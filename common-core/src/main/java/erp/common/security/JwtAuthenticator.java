package erp.common.security;

import org.springframework.security.core.Authentication;

/**
 * JWT 토큰을 검증하고 Authentication 으로 변환하는 전략 인터페이스.
 */
public interface JwtAuthenticator {
    Authentication authenticate(String token);
}
