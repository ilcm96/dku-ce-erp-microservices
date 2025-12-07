package erp.approvalrequest.domain;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import erp.shared.proto.approval.StepStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(collection = "approvals")
@CompoundIndexes({
        @CompoundIndex(name = "requester_idx", def = "{requesterId:1}", background = true),
        @CompoundIndex(name = "approver_idx", def = "{'steps.approverId':1}", background = true)
})
public class ApprovalDocument {
    @Id
    private String id;

    @Indexed(unique = true)
    private Long requestId;
    private Long requesterId;
    private String title;
    private String content;
    private List<StepInfo> steps;
    private Instant createdAt;
    private Instant updatedAt;
    private StepStatus finalStatus;

    @Version
    private Long version;

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setFinalStatus(StepStatus finalStatus) {
        this.finalStatus = finalStatus;
    }

    @Getter
    @Builder
    public static class StepInfo {
        private int step;
        private Long approverId;
        private StepStatus status;
        private Instant updatedAt;

        public void updateStatus(StepStatus status) {
            this.status = status;
            this.updatedAt = Instant.now();
        }
    }
}
