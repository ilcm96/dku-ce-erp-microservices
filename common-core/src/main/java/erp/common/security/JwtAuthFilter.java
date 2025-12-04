package erp.common.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;

/**
 * Authorization 헤더에서 Bearer 토큰을 추출해 인증하는 필터.
 * permitAll 경로는 필터를 건너뜁니다.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtAuthenticator jwtAuthenticator;
    private final List<String> permitAllPatterns;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final HandlerExceptionResolver handlerExceptionResolver;

    public JwtAuthFilter(
            JwtAuthenticator jwtAuthenticator,
            List<String> permitAllPatterns,
            HandlerExceptionResolver handlerExceptionResolver) {
        this.jwtAuthenticator = jwtAuthenticator;
        this.permitAllPatterns = permitAllPatterns;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return permitAllPatterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, uri));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new CustomException(ErrorCode.AUTH_TOKEN_MISSING);
            }
            String token = authHeader.substring(7);

            Authentication authentication = jwtAuthenticator.authenticate(token);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (CustomException e) {
            handlerExceptionResolver.resolveException(request, response, null, e);
        }
    }
}
