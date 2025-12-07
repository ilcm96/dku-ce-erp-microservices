package erp.employee.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;
import erp.common.security.AuthUtil;
import erp.common.security.Role;
import erp.employee.domain.Employee;
import erp.employee.dto.EmployeeRequest;
import erp.employee.dto.EmployeeResponse;
import erp.employee.dto.EmployeeUpdateRequest;
import erp.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthUtil authUtil;

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        enforceAdmin();
        if (employeeRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.EMPLOYEE_ALREADY_EXISTS);
        }
        Employee employee = Employee.builder()
                .email(request.email())
                .name(request.name())
                .department(request.department())
                .position(request.position())
                .role(request.role())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();
        Employee saved = employeeRepository.save(employee);
        return EmployeeResponse.from(saved);
    }

    public List<EmployeeResponse> findAll(String department, String position) {
        enforceAdmin();

        return employeeRepository.findAll().stream()
                .filter(e -> department == null || department.equals(e.getDepartment()))
                .filter(e -> position == null || position.equals(e.getPosition()))
                .map(EmployeeResponse::from)
                .toList();
    }

    public EmployeeResponse findById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        enforceSelfOrAdmin(id);
        return EmployeeResponse.from(employee);
    }

    public EmployeeResponse findByIdWithoutAuthorization(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        return EmployeeResponse.from(employee);
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeUpdateRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        enforceAdmin();
        employee.update(request.department(), request.position());
        return EmployeeResponse.from(employee);
    }

    @Transactional
    public void delete(Long id) {
        enforceAdmin();
        if (!employeeRepository.existsById(id)) {
            throw new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND);
        }
        employeeRepository.deleteById(id);
    }

    private void enforceAdmin() {
        if (!authUtil.hasRole(Role.ADMIN)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    private void enforceSelfOrAdmin(Long targetId) {
        Long currentId = authUtil.currentUserId();
        if (authUtil.hasRole(Role.ADMIN)) {
            return;
        }
        if (!currentId.equals(targetId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }
}
