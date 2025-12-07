package erp.employee.dto;

import erp.common.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EmployeeRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Size(max = 50) String department,
        @NotBlank @Size(max = 50) String position,
        @NotNull Role role,
        @NotBlank String password) {}
