package erp.employee.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class EmployeeSecurityIntegrationTest extends EmployeeIntegrationTestSupport {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Nested
    class Authorization {

        @Test
        void 토큰_없이_요청하면_401을_반환한다() throws Exception {
            // when & then: 예외 검증
            mockMvc.perform(get("/employees"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCodeName").value("AUTH_TOKEN_MISSING"));
        }

        @Test
        void EMPLOYEE_토큰으로_POST_요청하면_403을_반환한다() throws Exception {
            // given
            EmployeeRequest request = new EmployeeRequest(
                    "sec@example.com", "사용자", "개발팀", "사원", Role.EMPLOYEE, "pass1234!");

            // when & then: 예외 검증
            mockMvc.perform(post("/employees")
                            .header("X-User-Id", "2")
                            .header("X-User-Roles", "EMPLOYEE")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCodeName").value("FORBIDDEN"));
        }

        @Test
        void EMPLOYEE_토큰으로_목록을_조회하면_403을_반환한다() throws Exception {
            // when & then: 예외 검증
            mockMvc.perform(get("/employees")
                            .header("X-User-Id", "2")
                            .header("X-User-Roles", "EMPLOYEE"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCodeName").value("FORBIDDEN"));
        }

        @Test
        void 본인이_아닌_사용자_상세조회는_403을_반환한다() throws Exception {
            // given
            var other = saveEmployee("other@example.com", Role.EMPLOYEE);

            // when & then: 예외 검증
            mockMvc.perform(get("/employees/{id}", other.getId())
                            .header("X-User-Id", "999")
                            .header("X-User-Roles", "EMPLOYEE"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCodeName").value("FORBIDDEN"));
        }

        @Test
        void 토큰_없이_PUT_요청하면_401을_반환한다() throws Exception {
            // when & then: 예외 검증
            mockMvc.perform(put("/employees/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"department\":\"개발팀\",\"position\":\"사원\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCodeName").value("AUTH_TOKEN_MISSING"));
        }

        @Test
        void EMPLOYEE_토큰으로_DELETE_요청하면_403을_반환한다() throws Exception {
            // when & then: 예외 검증
            mockMvc.perform(delete("/employees/{id}", 1L)
                            .header("X-User-Id", "3")
                            .header("X-User-Roles", "EMPLOYEE"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCodeName").value("FORBIDDEN"));
        }

        @Test
        void ADMIN_토큰으로_POST_요청하면_201을_반환한다() throws Exception {
            // given
            EmployeeRequest request = new EmployeeRequest(
                    "sec-admin@example.com", "관리자", "개발팀", "매니저", Role.APPROVER, "pass1234!");

            // when & then: 반환값 검증
            mockMvc.perform(post("/employees")
                            .header("X-User-Id", "1")
                            .header("X-User-Roles", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value(request.email()))
                    .andExpect(jsonPath("$.role").value("APPROVER"));
        }
    }
}
