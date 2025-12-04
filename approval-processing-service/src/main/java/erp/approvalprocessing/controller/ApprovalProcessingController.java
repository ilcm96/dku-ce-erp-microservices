package erp.approvalprocessing.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import erp.approvalprocessing.dto.ApprovalQueueItemResponse;
import erp.approvalprocessing.service.ApprovalProcessingService;
import erp.shared.proto.approval.ApprovalResultStatus;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/process")
@RequiredArgsConstructor
public class ApprovalProcessingController {

    private final ApprovalProcessingService approvalProcessingService;

    @GetMapping("/{approverId}")
    @PreAuthorize("hasAnyRole('APPROVER','ADMIN')")
    public ResponseEntity<List<ApprovalQueueItemResponse>> queue(@PathVariable Long approverId) {
        return ResponseEntity.ok(approvalProcessingService.getQueue(approverId));
    }

    @PostMapping("/{approverId}/{requestId}")
    @PreAuthorize("hasAnyRole('APPROVER','ADMIN')")
    public ResponseEntity<Void> handle(
            @PathVariable Long approverId, @PathVariable Long requestId, @RequestBody StatusRequest body) {
        approvalProcessingService.handle(approverId, requestId, body.status());
        return ResponseEntity.accepted().build();
    }

    public record StatusRequest(ApprovalResultStatus status) {}
}
