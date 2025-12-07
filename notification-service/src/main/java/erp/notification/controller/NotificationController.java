package erp.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import erp.notification.config.WebSocketConfig.SessionStore;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class NotificationController {

    private final SessionStore sessionStore;

    @PostMapping("/notifications/{employeeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> send(@PathVariable Long employeeId, @Valid @RequestBody NotifyRequest request) {
        sessionStore.sendTo(employeeId, request.payload());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/internal/notifications/{employeeId}")
    public ResponseEntity<Void> sendInternal(@PathVariable Long employeeId, @Valid @RequestBody NotifyRequest request) {
        sessionStore.sendTo(employeeId, request.payload());
        return ResponseEntity.accepted().build();
    }

    public record NotifyRequest(@NotBlank String payload) {}
}
