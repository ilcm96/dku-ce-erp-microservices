package erp.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.core.MethodParameter;
import org.springframework.util.ReflectionUtils;

import java.util.Objects;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Nested
    @DisplayName("handleCustomException")
    class HandleCustomException {

        @Test
        void 커스텀_예외를_HTTP_응답으로_변환한다() {
            // given
            CustomException exception = new CustomException(ErrorCode.FORBIDDEN, "권한 없음");

            // when
            ResponseEntity<ErrorResponse> response = handler.handleCustomException(exception);

            // then: 반환값 검증
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().errorCodeName()).isEqualTo(ErrorCode.FORBIDDEN.name());
            assertThat(response.getBody().errorMessage()).isEqualTo(ErrorCode.FORBIDDEN.getMessage());
        }
    }

    @Nested
    @DisplayName("handleDataIntegrityViolationException")
    class HandleDataIntegrityViolationException {

        @Test
        void 데이터_무결성_예외를_409로_매핑한다() {
            // given
            DataIntegrityViolationException exception = new DataIntegrityViolationException("duplicate");

            // when
            ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolationException(exception);

            // then: 반환값 검증
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().errorCodeName())
                    .isEqualTo(ErrorCode.DATA_INTEGRITY_VIOLATION.name());
        }
    }

    @Nested
    @DisplayName("handleException")
    class HandleException {

        @Test
        void 알수없는_예외는_500으로_매핑한다() {
            // given
            RuntimeException exception = new RuntimeException("unexpected");

            // when
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // then: 반환값 검증
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().errorCodeName())
                    .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.name());
        }
    }

    @Nested
    @DisplayName("handleMethodArgumentNotValid")
    class HandleMethodArgumentNotValid {

        @Test
        void 필드_에러를_ArgumentNotValidErrorResponse로_반환한다() throws NoSuchMethodException {
            // given
            MethodParameter parameter = new MethodParameter(
                    Objects.requireNonNull(ReflectionUtils.findMethod(SampleController.class, "handle", SampleRequest.class)), 0);
            SampleRequest target = new SampleRequest();
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "sampleRequest");
            bindingResult.addError(new FieldError("sampleRequest", "name", target.getName(), false, null, null,
                    "must not be blank"));

            MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

            // when
            ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                    exception, HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, mock(WebRequest.class));

            // then: 반환값 검증
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isInstanceOf(ArgumentNotValidErrorResponse.class);

            ArgumentNotValidErrorResponse body = (ArgumentNotValidErrorResponse) response.getBody();
            assertThat(body.errorCodeName()).isEqualTo(ErrorCode.BAD_REQUEST.name());
            assertThat(body.errors()).hasSize(1);
            assertThat(body.errors().getFirst().field()).isEqualTo("name");
            assertThat(body.errors().getFirst().message()).contains("blank");
        }
    }

    @Nested
    @DisplayName("handleHttpMessageNotReadable")
    class HandleHttpMessageNotReadable {

        @Test
        void 잘못된_JSON_형식이면_INVALID_JSON을_반환한다() {
            // given
            JsonParseException jsonParseException = new JsonParseException((JsonParser) null, "bad json");
            HttpMessageNotReadableException exception =
                    new HttpMessageNotReadableException("invalid", jsonParseException, null);

            // when
            ResponseEntity<Object> response = handler.handleHttpMessageNotReadable(
                    exception, HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, mock(WebRequest.class));

            // then: 반환값 검증
            assertThat(response.getStatusCode()).isEqualTo(ErrorCode.INVALID_JSON.getHttpStatus());
            assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
            assertThat(((ErrorResponse) response.getBody()).errorCodeName())
                    .isEqualTo(ErrorCode.INVALID_JSON.name());
        }

        @Test
        void 잘못된_Enum_값이면_INVALID_ENUM을_반환한다() {
            // given
            IllegalArgumentException cause = new IllegalArgumentException("invalid enum");
            HttpMessageNotReadableException exception =
                    new HttpMessageNotReadableException("invalid", cause, null);

            // when
            ResponseEntity<Object> response = handler.handleHttpMessageNotReadable(
                    exception, HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, mock(WebRequest.class));

            // then: 반환값 검증
            assertThat(response.getStatusCode()).isEqualTo(ErrorCode.INVALID_ENUM.getHttpStatus());
            assertThat(((ErrorResponse) response.getBody()).errorCodeName())
                    .isEqualTo(ErrorCode.INVALID_ENUM.name());
        }

        @Test
        void 기타_HTTP_메시지_읽기_실패는_BAD_REQUEST를_반환한다() {
            // given
            HttpMessageNotReadableException exception =
                    new HttpMessageNotReadableException("invalid body", new RuntimeException("parse error"), null);

            // when
            ResponseEntity<Object> response = handler.handleHttpMessageNotReadable(
                    exception, HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, mock(WebRequest.class));

            // then: 반환값 검증
            assertThat(response.getStatusCode()).isEqualTo(ErrorCode.BAD_REQUEST.getHttpStatus());
            assertThat(((ErrorResponse) response.getBody()).errorCodeName())
                    .isEqualTo(ErrorCode.BAD_REQUEST.name());
        }
    }

    private static class SampleController {
        public void handle(@Valid SampleRequest request) {
        }
    }

    private static class SampleRequest {
        @NotBlank
        private String name;

        public String getName() {
            return name;
        }
    }
}
