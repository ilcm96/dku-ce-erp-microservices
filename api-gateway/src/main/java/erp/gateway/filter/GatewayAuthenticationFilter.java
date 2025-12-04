package erp.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayAuthenticationFilter implements GlobalFilter {

    private static final List<String> SKIP_PATTERNS = List.of("/actuator/**", "/ws/**", "/auth/**");
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    private final String jwtSecret;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewayAuthenticationFilter(
            @Value("${security.jwt.secret:}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (SKIP_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path))) {
            return chain.filter(exchange);
        }

        if (path.startsWith("/internal/")) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        Optional<UserContext> userContext = authenticate(token);

        if (userContext.isEmpty()) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(HttpHeaders.AUTHORIZATION);
                    headers.set(HEADER_USER_ID, String.valueOf(userContext.get().userId()));
                    headers.set(HEADER_USER_ROLES, String.join(",", userContext.get().roles()));
                })
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private Optional<UserContext> authenticate(String token) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            return Optional.empty();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = Long.valueOf(claims.getSubject());
            Object rolesClaim = claims.get("roles");
            List<String> roles;
            if (rolesClaim instanceof List<?> list) {
                roles = list.stream().map(Object::toString).toList();
            } else {
                roles = List.of(String.valueOf(Optional.ofNullable(rolesClaim).orElse("EMPLOYEE")));
            }
            return Optional.of(new UserContext(userId, roles));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private record UserContext(Long userId, List<String> roles) {}
}
