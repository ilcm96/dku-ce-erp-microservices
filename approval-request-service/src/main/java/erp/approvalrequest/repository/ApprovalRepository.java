package erp.approvalrequest.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import erp.approvalrequest.domain.ApprovalDocument;

public interface ApprovalRepository extends MongoRepository<ApprovalDocument, String> {
    List<ApprovalDocument> findByRequesterId(Long requesterId);

    List<ApprovalDocument> findByRequesterIdOrStepsApproverId(Long requesterId, Long approverId);

    Optional<ApprovalDocument> findByRequestId(Long requestId);
}
