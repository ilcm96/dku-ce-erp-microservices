package erp.employee.dto;

import java.time.Instant;
import java.util.List;

import erp.common.security.Role;

public record LoginResponse(
        String token,
        String tokenType,
        Instant expiresAt,
        Long userId,
        List<Role> roles
) {}
