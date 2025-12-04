package erp.employee.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import erp.common.security.Role;
import erp.employee.domain.Employee;
import erp.employee.repository.EmployeeRepository;

/**
 * 테스트 외 환경에서 기본 ADMIN 계정을 시드합니다.
 */
@Configuration
@ConditionalOnProperty(prefix = "employee.admin.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AdminSeedConfig {

    @Bean
    CommandLineRunner adminSeeder(
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder,
            @Value("${employee.admin.seed.email}") String email,
            @Value("${employee.admin.seed.name}") String name,
            @Value("${employee.admin.seed.department}") String department,
            @Value("${employee.admin.seed.position}") String position,
            @Value("${employee.admin.seed.role}") String role,
            @Value("${employee.admin.seed.password}") String password) {

        return args -> {
            if (employeeRepository.existsByEmail(email)) {
                return;
            }
            Role seedRole = Role.valueOf(role.toUpperCase());
            String hashed = passwordEncoder.encode(password);
            Employee admin = Employee.builder()
                    .email(email)
                    .name(name)
                    .department(department)
                    .position(position)
                    .role(seedRole)
                    .passwordHash(hashed)
                    .build();
            employeeRepository.save(admin);
        };
    }
}
