package erp.approvalrequest.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import erp.approvalrequest.dto.ApprovalCreateRequest;
import erp.approvalrequest.dto.ApprovalResponse;
import erp.approvalrequest.service.ApprovalRequestService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalRequestService approvalRequestService;

    @PostMapping
    public ResponseEntity<ApprovalResponse> create(@Validated @RequestBody ApprovalCreateRequest request) {
        ApprovalResponse response = approvalRequestService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/resend")
    public ResponseEntity<Void> resend(@PathVariable("id") Long requestId) {
        approvalRequestService.resendPending(requestId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping
    public ResponseEntity<List<ApprovalResponse>> list() {
        return ResponseEntity.ok(approvalRequestService.listForCurrentUser());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApprovalResponse> get(@PathVariable("id") Long requestId) {
        return ResponseEntity.ok(approvalRequestService.findOne(requestId));
    }
}
