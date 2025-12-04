package erp.common.security;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * 실제 JWT 서명 검증을 수행하는 인증기.
 * HS256 비밀키 기반. 환경변수/설정에서 security.jwt.secret 로 주입.
 */
@Component
public class RealJwtAuthenticator implements JwtAuthenticator {

    private final byte[] secret;

    public RealJwtAuthenticator(@Value("${security.jwt.secret}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("security.jwt.secret 값이 필요합니다.");
        }
        this.secret = secret.getBytes();
    }

    @Override
    public Authentication authenticate(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(secret))
                    .build()
                    .parseSignedClaims(token);

            Claims claims = jws.getPayload();
            Object sub = claims.get("sub");
            if (sub == null) {
                throw new CustomException(ErrorCode.AUTH_INVALID_TOKEN);
            }
            Long userId = Long.valueOf(sub.toString());

            List<Role> roles = extractRoles(claims);
            AuthenticatedUser principal = new AuthenticatedUser(userId, roles);
            return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        } catch (Exception e) {
            throw new CustomException(ErrorCode.AUTH_INVALID_TOKEN);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Role> extractRoles(Claims claims) {
        Object raw = claims.get("roles");
        if (raw == null) {
            return List.of(Role.EMPLOYEE);
        }
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::toUpperCase)
                    .map(Role::valueOf)
                    .collect(Collectors.toList());
        }
        return List.of(Role.valueOf(raw.toString().toUpperCase()));
    }
}
