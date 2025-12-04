package erp.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorCodeTest {

    @Nested
    @DisplayName("httpStatus/메시지 매핑")
    class HttpStatusAndMessageMapping {

        @Test
        void 인증_및_권한_에러_매핑을_검증한다() {
            // when & then: 반환값 검증
            assertThat(ErrorCode.AUTH_TOKEN_MISSING.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(ErrorCode.AUTH_TOKEN_MISSING.getMessage()).isEqualTo("인증 토큰이 없습니다.");

            assertThat(ErrorCode.AUTH_INVALID_TOKEN.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(ErrorCode.FORBIDDEN.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void 직원_및_결재_관련_에러_매핑을_검증한다() {
            // when & then: 반환값 검증
            assertThat(ErrorCode.EMPLOYEE_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(ErrorCode.EMPLOYEE_ALREADY_EXISTS.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);

            assertThat(ErrorCode.APPROVAL_REQUEST_INVALID_STEP.getHttpStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ErrorCode.APPROVAL_PROCESS_INVALID_STATUS.getHttpStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 알림_에러_매핑을_검증한다() {
            // when & then: 반환값 검증
            assertThat(ErrorCode.NOTIFICATION_SESSION_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(ErrorCode.NOTIFICATION_SESSION_NOT_FOUND.getMessage()).isEqualTo("알림 세션을 찾을 수 없습니다.");
        }
    }
}
