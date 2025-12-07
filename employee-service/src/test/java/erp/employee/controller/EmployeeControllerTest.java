package erp.employee.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import erp.common.security.Role;
import erp.employee.EmployeeIntegrationTestSupport;
import erp.employee.dto.EmployeeRequest;

@AutoConfigureMockMvc
class EmployeeControllerTest extends EmployeeIntegrationTestSupport {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Nested
    class List {

        @Test
        void ADMIN_토큰으로_부서와_직책을_필터링할_수_있다() throws Exception {
            // given
            saveEmployee("dev1@example.com", "개발자1", "개발팀", "사원", Role.EMPLOYEE, "pass1234!");
            saveEmployee("dev2@example.com", "개발자2", "개발팀", "매니저", Role.APPROVER, "pass1234!");
            saveEmployee("sales1@example.com", "영업", "영업팀", "사원", Role.EMPLOYEE, "pass1234!");

            // when & then: 반환값 검증
            mockMvc.perform(get("/employees")
                            .header("X-User-Id", "1")
                            .header("X-User-Roles", "ADMIN")
                            .param("department", "개발팀")
                            .param("position", "사원"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", Matchers.is(1)))
                    .andExpect(jsonPath("$[0].email").value("dev1@example.com"));
        }
    }

    @Nested
    class Create {

        @Test
        void ADMIN_토큰으로_직원을_생성하면_201을_반환한다() throws Exception {
            // given
            EmployeeRequest request = new EmployeeRequest(
                    "controller@example.com", "홍길동", "개발팀", "리드", Role.ADMIN, "pass1234!");

            // when & then: 반환값 검증
            mockMvc.perform(post("/employees")
                            .header("X-User-Id", "1")
                            .header("X-User-Roles", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.email").value(request.email()))
                    .andExpect(jsonPath("$.role").value("ADMIN"));
        }

        @Test
        void 필수_값이_누락되면_400과_BAD_REQUEST_에러를_반환한다() throws Exception {
            // given
            String invalidPayload = "{\n" +
                    "  \"email\": \"invalid@example.com\",\n" +
                    "  \"department\": \"개발팀\",\n" +
                    "  \"position\": \"사원\",\n" +
                    "  \"role\": \"EMPLOYEE\",\n" +
                    "  \"password\": \"pass1234!\"\n" +
                    "}";

            // when & then: 예외 검증
            mockMvc.perform(post("/employees")
                            .header("X-User-Id", "1")
                            .header("X-User-Roles", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidPayload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCodeName").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("name"));
        }
    }
}
