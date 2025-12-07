package erp.employee.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;
import erp.common.security.Role;
import erp.employee.domain.Employee;
import erp.employee.dto.LoginRequest;
import erp.employee.dto.LoginResponse;
import erp.employee.repository.EmployeeRepository;
import erp.employee.security.JwtTokenProvider;
import erp.employee.security.JwtTokenProvider.JwtToken;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResponse login(LoginRequest request) {
        Employee employee = employeeRepository.findByEmail(request.email())
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.password(), employee.getPasswordHash())) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        List<Role> roles = expandRoles(employee.getRole());
        JwtToken jwtToken = jwtTokenProvider.generateToken(employee.getId(), roles);

        return new LoginResponse(jwtToken.token(), "Bearer", jwtToken.expiresAt(), employee.getId(), roles);
    }

    private List<Role> expandRoles(Role role) {
        return switch (role) {
            case EMPLOYEE -> List.of(Role.EMPLOYEE);
            case APPROVER -> List.of(Role.EMPLOYEE, Role.APPROVER);
            case ADMIN -> List.of(Role.EMPLOYEE, Role.APPROVER, Role.ADMIN);
        };
    }
}
