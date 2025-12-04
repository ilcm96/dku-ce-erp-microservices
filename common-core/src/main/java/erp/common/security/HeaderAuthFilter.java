package erp.common.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;

/**
 * Gateway가 주입한 사용자 헤더(`X-User-Id`, `X-User-Roles`)로 인증을 구성하는 필터.
 * JWT 검증은 Gateway에서만 수행하며, 서비스는 헤더 신뢰를 전제로 한다.
 */
public class HeaderAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    private final List<String> permitAllPatterns;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final HandlerExceptionResolver handlerExceptionResolver;

    public HeaderAuthFilter(List<String> permitAllPatterns, HandlerExceptionResolver handlerExceptionResolver) {
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
            String userIdHeader = request.getHeader(HEADER_USER_ID);
            String rolesHeader = request.getHeader(HEADER_USER_ROLES);

            if (userIdHeader == null || rolesHeader == null) {
                throw new CustomException(ErrorCode.AUTH_TOKEN_MISSING);
            }

            long userId;
            try {
                userId = Long.parseLong(userIdHeader);
            } catch (NumberFormatException e) {
                throw new CustomException(ErrorCode.AUTH_INVALID_TOKEN);
            }

            List<Role> roles = Arrays.stream(rolesHeader.split(","))
                    .filter(part -> !part.isBlank())
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .map(Role::valueOf)
                    .toList();

            if (roles.isEmpty()) {
                throw new CustomException(ErrorCode.AUTH_INVALID_TOKEN);
            }

            AuthenticatedUser principal = new AuthenticatedUser(userId, roles);
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (CustomException e) {
            handlerExceptionResolver.resolveException(request, response, null, e);
        }
    }
}
