package erp.approvalrequest.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationClient {

    @Value("${notification.base-url}")
    private String baseUrl;

    @Value("${notification.retry.max-attempts}")
    private int maxAttempts;

    @Value("${notification.retry.backoff-millis}")
    private long backoffMillis;

    private final RestClient.Builder restClientBuilder;

    public void send(Long employeeId, String payload) {
        for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
            try {
                restClientBuilder
                        .build()
                        .post()
                        .uri(baseUrl + "/internal/notifications/" + employeeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new NotificationPayload(payload))
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (Exception e) {
                log.warn("Notification send failed (attempt {}/{} ) for user {}: {}", attempt, maxAttempts, employeeId, e.getMessage());
                sleepQuietly();
            }
        }
    }

    public record NotificationPayload(String payload) {}

    private void sleepQuietly() {
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
