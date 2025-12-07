package erp.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("HeaderAuthFilter")
class HeaderAuthFilterTest {

    @Mock
    private HandlerExceptionResolver handlerExceptionResolver;

    private HeaderAuthFilter headerAuthFilter;

    @BeforeEach
    void setUp() {
        headerAuthFilter = new HeaderAuthFilter(List.of("/actuator/**", "/ws/**"), handlerExceptionResolver);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("shouldNotFilter")
    class ShouldNotFilter {

        @Test
        void permitAll_경로에서는_필터를_적용하지_않는다() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

            // when
            boolean result = headerAuthFilter.shouldNotFilter(request);

            // then: 반환값 검증
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("doFilterInternal")
    class DoFilterInternal {

        @Test
        void Authorization_헤더가_없으면_AUTH_TOKEN_MISSING을_던진다() throws Exception {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);

            given(handlerExceptionResolver.resolveException(eq(request), eq(response), isNull(), any()))
                    .willReturn(null);

            // when & then: 예외 검증
            headerAuthFilter.doFilterInternal(request, response, filterChain);

            ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
            verify(handlerExceptionResolver).resolveException(eq(request), eq(response), isNull(), captor.capture());
            assertThat(captor.getValue())
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_TOKEN_MISSING);
            verifyNoInteractions(filterChain);
        }

        @Test
        void 헤더를_인증하고_SecurityContext에_저장한다() throws Exception {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api");
            request.addHeader("X-User-Id", "1");
            request.addHeader("X-User-Roles", "ADMIN,EMPLOYEE");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);

            AuthenticatedUser principal = new AuthenticatedUser(1L, List.of(Role.ADMIN, Role.EMPLOYEE));
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());

            // when
            headerAuthFilter.doFilterInternal(request, response, filterChain);

            // then: 반환값 검증
            Authentication result = SecurityContextHolder.getContext().getAuthentication();
            assertThat(result).isNotNull();
            assertThat(result.getPrincipal()).isInstanceOf(AuthenticatedUser.class);
            AuthenticatedUser authenticated = (AuthenticatedUser) result.getPrincipal();
            assertThat(authenticated.userId()).isEqualTo(1L);
            assertThat(authenticated.roles()).containsExactly(Role.ADMIN, Role.EMPLOYEE);
            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(handlerExceptionResolver);
        }
    }
}
