package erp.employee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.mockito.Mockito;

import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;
import erp.common.security.AuthUtil;
import erp.common.security.Role;
import erp.employee.EmployeeIntegrationTestSupport;
import erp.employee.domain.Employee;
import erp.employee.dto.EmployeeRequest;
import erp.employee.dto.EmployeeResponse;
import erp.employee.dto.EmployeeUpdateRequest;
import erp.employee.repository.EmployeeRepository;

@Import(EmployeeServiceTest.MockConfig.class)
class EmployeeServiceTest extends EmployeeIntegrationTestSupport {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AuthUtil authUtil;

    @TestConfiguration
    static class MockConfig {

        @Bean
        @Primary
        AuthUtil authUtil() {
            return Mockito.mock(AuthUtil.class);
        }
    }

    @Nested
    class Create {

        @Test
        void ADMIN_권한이면_직원을_생성한다() {
            // given
            EmployeeRequest request = new EmployeeRequest(
                    "admin@example.com", "어드민", "개발팀", "리드", Role.ADMIN, "pass1234!");
            given(authUtil.hasRole(Role.ADMIN)).willReturn(true);

            // when
            EmployeeResponse response = employeeService.create(request);

            // then: 반환값 검증
            assertThat(response.id()).isNotNull();
            assertThat(response.email()).isEqualTo(request.email());
            assertThat(response.role()).isEqualTo(request.role());

            // then: DB 상태 검증
            Employee saved = employeeRepository.findById(response.id()).orElseThrow();
            assertThat(saved.getEmail()).isEqualTo(request.email());
            assertThat(saved.getRole()).isEqualTo(request.role());
            assertThat(saved.getPasswordHash()).isNotEqualTo(request.password());
            assertThat(passwordEncoder.matches(request.password(), saved.getPasswordHash())).isTrue();
        }

        @Test
        void 이메일이_중복되면_EMPLOYEE_ALREADY_EXISTS를_던진다() {
            // given
            saveEmployee("dup@example.com", Role.EMPLOYEE);
            EmployeeRequest request = new EmployeeRequest(
                    "dup@example.com", "사용자", "개발팀", "주임", Role.EMPLOYEE, "pass1234!");
            given(authUtil.hasRole(Role.ADMIN)).willReturn(true);

            // when & then: 예외 검증
            assertThatThrownBy(() -> employeeService.create(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EMPLOYEE_ALREADY_EXISTS);
        }

        @Test
        void ADMIN이_아니면_FORBIDDEN을_던진다() {
            // given
            EmployeeRequest request = new EmployeeRequest(
                    "user@example.com", "사용자", "개발팀", "사원", Role.EMPLOYEE, "pass1234!");
            given(authUtil.hasRole(Role.ADMIN)).willReturn(false);

            // when & then: 예외 검증
            assertThatThrownBy(() -> employeeService.create(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }
    }

    @Nested
    class FindAll {

        @Test
        void ADMIN이면_모든_직원을_조회한다() {
            // given
            saveEmployee("a@example.com", Role.EMPLOYEE);
            saveEmployee("b@example.com", Role.APPROVER);
            given(authUtil.hasRole(Role.ADMIN)).willReturn(true);

            // when
            List<EmployeeResponse> responses = employeeService.findAll(null, null);

            // then: 반환값 검증
            assertThat(responses).hasSize(2);
            assertThat(responses)
                    .extracting(EmployeeResponse::email)
                    .containsExactlyInAnyOrder("a@example.com", "b@example.com");
        }

        @Test
        void ADMIN이_아니면_FORBIDDEN을_던진다() {
            // given
            given(authUtil.hasRole(Role.ADMIN)).willReturn(false);

            // when & then: 예외 검증
            assertThatThrownBy(() -> employeeService.findAll(null, null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        void ADMIN이면_부서와_직책으로_필터링할_수_있다() {
            // given
            saveEmployee("dev@example.com", "개발자", "개발팀", "사원", Role.EMPLOYEE, "pass1234!");
            saveEmployee("sales@example.com", "영업", "영업팀", "사원", Role.EMPLOYEE, "pass1234!");
            saveEmployee("dev-manager@example.com", "리드", "개발팀", "매니저", Role.ADMIN, "pass1234!");
            given(authUtil.hasRole(Role.ADMIN)).willReturn(true);

            // when
            List<EmployeeResponse> responses = employeeService.findAll("개발팀", "사원");

            // then: 반환값 검증
            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().email()).isEqualTo("dev@example.com");
        }
    }

    @Nested
    class FindById {

        @Test
        void ADMIN이면_다른_사용자도_조회할_수_있다() {
            // given
            Employee employee = saveEmployee("target@example.com", Role.EMPLOYEE);
            given(authUtil.currentUserId()).willReturn(99L);
            given(authUtil.hasRole(Role.ADMIN)).willReturn(true);

            // when
            EmployeeResponse response = employeeService.findById(employee.getId());

            // then: 반환값 검증
            assertThat(response.id()).isEqualTo(employee.getId());
            assertThat(response.email()).isEqualTo(employee.getEmail());
        }

        @Test
        void 본인_조회는_허용된다() {
            // given
            Employee employee = saveEmployee("me@example.com", Role.EMPLOYEE);
            given(authUtil.currentUserId()).willReturn(employee.getId());
            given(authUtil.hasRole(Role.ADMIN)).willReturn(false);

            // when
            EmployeeResponse response = employeeService.findById(employee.getId());

            // then: 반환값 검증
            assertThat(response.id()).isEqualTo(employee.getId());
            assertThat(response.email()).isEqualTo("me@example.com");
        }

        @Test
        void 다른_사용자를_조회하면_FORBIDDEN을_던진다() {
            // given
            Employee employee = saveEmployee("other@example.com", Role.EMPLOYEE);
            given(authUtil.currentUserId()).willReturn(employee.getId() + 1);
            given(authUtil.hasRole(Role.ADMIN)).willReturn(false);

            // when & then: 예외 검증
            assertThatThrownBy(() -> employeeService.findById(employee.getId()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        void 존재하지_않으면_EMPLOYEE_NOT_FOUND를_던진다() {
            // when & then: 예외 검증
            assertThatThrownBy(() -> employeeService.findById(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EMPLOYEE_NOT_FOUND);
        }
    }

    @Nested
    class Update {

        @Test
        void ADMIN이면_부서와_직책을_수정한다() {
            // given
            Employee employee = saveEmployee("target@example.com", Role.EMPLOYEE);
            EmployeeUpdateRequest request = new EmployeeUpdateRequest("영업팀", "매니저");
            given(authUtil.hasRole(Role.ADMIN)).willReturn(true);

            // when
            EmployeeResponse response = employeeService.update(employee.getId(), request);

            // then: 반환값 검증
            assertThat(response.department()).isEqualTo("영업팀");
            assertThat(response.position()).isEqualTo("매니저");

            // then: DB 상태 검증
            Employee updated = employeeRepository.findById(employee.getId()).orElseThrow();
            assertThat(updated.getDepartment()).isEqualTo("영업팀");
            assertThat(updated.getPosition()).isEqualTo("매니저");
        }

        @Test
        void 존재하지_않으면_EMPLOYEE_NOT_FOUND를_던진다() {
            // given
            EmployeeUpdateRequest request = new EmployeeUpdateRequest("영업팀", "매니저");
            given(authUtil.hasRole(Role.ADMIN)).willReturn(true);

            // when & then: 예외 검증
            assertThatThrownBy(() -> employeeService.update(999L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EMPLOYEE_NOT_FOUND);
        }

        @Test
        void ADMIN이_아니면_FORBIDDEN을_던진다() {
            // given
            Employee employee = saveEmployee("target@example.com", Role.EMPLOYEE);
            EmployeeUpdateRequest request = new EmployeeUpdateRequest("영업팀", "매니저");
            given(authUtil.hasRole(Role.ADMIN)).willReturn(false);

            // when & then: 예외 검증
            assertThatThrownBy(() -> employeeService.update(employee.getId(), request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }
    }

    @Nested
    class Delete {

        @Test
        void ADMIN이면_직원을_삭제한다() {
            // given
            Employee employee = saveEmployee("target@example.com", Role.EMPLOYEE);
            given(authUtil.hasRole(Role.ADMIN)).willReturn(true);

            // when
            employeeService.delete(employee.getId());

            // then: DB 상태 검증
            assertThat(employeeRepository.existsById(employee.getId())).isFalse();
        }

        @Test
        void 존재하지_않으면_EMPLOYEE_NOT_FOUND를_던진다() {
            // given
            given(authUtil.hasRole(Role.ADMIN)).willReturn(true);

            // when & then: 예외 검증
            assertThatThrownBy(() -> employeeService.delete(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EMPLOYEE_NOT_FOUND);
        }

        @Test
        void ADMIN이_아니면_FORBIDDEN을_던진다() {
            // given
            given(authUtil.hasRole(Role.ADMIN)).willReturn(false);

            // when & then: 예외 검증
            assertThatThrownBy(() -> employeeService.delete(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }
    }
}
