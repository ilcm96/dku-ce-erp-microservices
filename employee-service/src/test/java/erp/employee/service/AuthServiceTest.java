package erp.employee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;
import erp.common.security.Role;
import erp.employee.EmployeeIntegrationTestSupport;
import erp.employee.dto.LoginRequest;
import erp.employee.dto.LoginResponse;

@DisplayName("AuthService")
class AuthServiceTest extends EmployeeIntegrationTestSupport {

    @Autowired
    private AuthService authService;

    @Test
    @DisplayName("유효한 이메일/비밀번호로 로그인하면 JWT와 사용자 정보를 반환한다")
    void login_success() {
        // given
        Long userId = saveEmployee("user@example.com", "홍길동", "개발", "사원", Role.APPROVER, "password123!").getId();
        LoginRequest request = new LoginRequest("user@example.com", "password123!");

        // when
        LoginResponse response = authService.login(request);

        // then
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.roles()).containsExactly(Role.EMPLOYEE, Role.APPROVER);
        assertThat(response.token()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("이메일이 존재하지 않으면 UNAUTHORIZED 예외를 던진다")
    void login_emailNotFound() {
        // given
        LoginRequest request = new LoginRequest("notfound@example.com", "password123!");

        // when & then: 예외 검증
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 UNAUTHORIZED 예외를 던진다")
    void login_invalidPassword() {
        // given
        saveEmployee("user@example.com", "홍길동", "개발", "사원", Role.EMPLOYEE, "password123!");
        LoginRequest request = new LoginRequest("user@example.com", "wrong-password");

        // when & then: 예외 검증
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
