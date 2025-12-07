package erp.notification.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import erp.common.security.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

public final class TestJwtFactory {

    public static final String SECRET = "test-secret-key-should-be-long-enough-32bytes";

    private TestJwtFactory() {
    }

    public static String createToken(Long userId, List<Role> roles) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(1, ChronoUnit.DAYS);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("roles", roles.stream().map(Role::name).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
