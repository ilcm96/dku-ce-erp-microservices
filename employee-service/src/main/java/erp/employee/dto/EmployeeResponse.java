package erp.employee.dto;

import erp.common.security.Role;
import erp.employee.domain.Employee;

public record EmployeeResponse(
        Long id, String email, String name, String department, String position, Role role) {

    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getEmail(),
                employee.getName(),
                employee.getDepartment(),
                employee.getPosition(),
                employee.getRole());
    }
}
