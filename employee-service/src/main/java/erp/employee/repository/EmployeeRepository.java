package erp.employee.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import erp.employee.domain.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    boolean existsByEmail(String email);
    Optional<Employee> findByEmail(String email);
}
