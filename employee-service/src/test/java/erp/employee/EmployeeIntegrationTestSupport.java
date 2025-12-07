package erp.employee;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.BeforeEach;

import erp.common.security.Role;
import erp.employee.domain.Employee;
import erp.employee.repository.EmployeeRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class EmployeeIntegrationTestSupport {

    @Autowired
    protected EmployeeRepository employeeRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        employeeRepository.deleteAll();
    }

    protected Employee saveEmployee(String email, Role role) {
        return saveEmployee(email, "홍길동", "개발팀", "사원", role, "password123!");
    }

    protected Employee saveEmployee(
            String email, String name, String department, String position, Role role, String rawPassword) {
        Employee employee = Employee.builder()
                .email(email)
                .name(name)
                .department(department)
                .position(position)
                .role(role)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .build();
        return employeeRepository.save(employee);
    }
}
