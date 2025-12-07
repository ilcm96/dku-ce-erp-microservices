package erp.common.exception;

public record FieldErrorDetail(String field, Object rejectedValue, String message) {
    public static FieldErrorDetail of(String field, Object rejectedValue, String message) {
        return new FieldErrorDetail(field, rejectedValue, message);
    }
}
