package erp.approvalrequest.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;
import erp.common.security.Role;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EmployeeClient {

    @Value("${employee.base-url}")
    private String baseUrl;

    private final RestClient.Builder restClientBuilder;

    public EmployeeDto findById(Long id) {
        try {
            return restClientBuilder
                    .build()
                    .get()
                    .uri(baseUrl + "/internal/employees/{id}", id)
                    .retrieve()
                    .body(EmployeeDto.class);
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND);
        }
    }

    public Role findRole(Long id) {
        return findById(id).role();
    }

    public record EmployeeDto(Long id, String email, String name, String department, String position, Role role) {}
}
