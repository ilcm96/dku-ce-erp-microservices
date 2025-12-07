package erp.common.exception;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Value("${exception.log-only-app-stack-trace:false}")
    private boolean logOnlyAppStackTrace;

    private static final String BASE_PACKAGE = "erp";

    private void logByType(Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());

        List<StackTraceElement> stackTraceElements;
        if (logOnlyAppStackTrace) {
            stackTraceElements = Arrays.stream(e.getStackTrace())
                    .filter(element -> element.getClassName().startsWith(BASE_PACKAGE))
                    .toList();
            if (stackTraceElements.isEmpty()) {
                stackTraceElements = Arrays.asList(e.getStackTrace());
            }
        } else {
            stackTraceElements = Arrays.asList(e.getStackTrace());
        }

        for (StackTraceElement element : stackTraceElements) {
            sb.append("\n")
                    .append("\tat ")
                    .append(element.getClassName())
                    .append(".")
                    .append(element.getMethodName())
                    .append("(")
                    .append(element.getFileName())
                    .append(":")
                    .append(element.getLineNumber())
                    .append(")");
        }

        if (e instanceof CustomException customException) {
            if (customException.getErrorCode().getHttpStatus().value() >= 500) {
                log.error(sb.toString());
            } else {
                log.warn(sb.toString());
            }
        } else if (e instanceof DataIntegrityViolationException
                || e instanceof MethodArgumentNotValidException
                || e instanceof HttpMessageNotReadableException) {
            log.warn(sb.toString());
        } else {
            log.error(sb.toString());
        }
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        logByType(e);
        return ResponseEntity.status(e.getErrorCode().getHttpStatus()).body(ErrorResponse.of(e.getErrorCode()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        logByType(e);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(ErrorCode.DATA_INTEGRITY_VIOLATION));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        logByType(e);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        logByType(e);

        List<FieldErrorDetail> fieldErrorDetails = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> FieldErrorDetail.of(
                        fieldError.getField(), fieldError.getRejectedValue(), fieldError.getDefaultMessage()))
                .collect(Collectors.toList());

        ArgumentNotValidErrorResponse errorResponse =
                ArgumentNotValidErrorResponse.of(ErrorCode.BAD_REQUEST, fieldErrorDetails);

        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getHttpStatus()).body(errorResponse);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        logByType(e);

        Throwable cause = e.getCause();

        if (cause instanceof JsonParseException
                || cause instanceof MismatchedInputException) {
            return ResponseEntity.status(ErrorCode.INVALID_JSON.getHttpStatus())
                    .body(ErrorResponse.of(ErrorCode.INVALID_JSON));
        }

        if (cause instanceof IllegalArgumentException) {
            return ResponseEntity.status(ErrorCode.INVALID_ENUM.getHttpStatus())
                    .body(ErrorResponse.of(ErrorCode.INVALID_ENUM));
        }

        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.BAD_REQUEST));
    }
}
