package erp.approvalrequest;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import erp.approvalrequest.domain.ApprovalDocument;
import erp.approvalrequest.repository.ApprovalRepository;
import erp.approvalrequest.service.RequestIdGenerator;
import erp.shared.proto.approval.StepStatus;
import erp.approvalrequest.support.TestRabbitConfig;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Import(TestRabbitConfig.class)
public abstract class ApprovalRequestIntegrationTestSupport {

    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.2");

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @Autowired
    protected ApprovalRepository approvalRepository;

    @Autowired
    protected MongoTemplate mongoTemplate;

    @Autowired
    protected RequestIdGenerator requestIdGenerator;

    @BeforeEach
    void cleanDatabase() {
        mongoTemplate.getDb().drop();
    }

    protected ApprovalDocument.StepInfo step(int step, Long approverId, StepStatus status) {
        return ApprovalDocument.StepInfo.builder()
                .step(step)
                .approverId(approverId)
                .status(status)
                .build();
    }

    protected ApprovalDocument saveDocument(
            Long requesterId, List<ApprovalDocument.StepInfo> steps, StepStatus finalStatus) {
        ApprovalDocument doc = ApprovalDocument.builder()
                .requestId(requestIdGenerator.nextId())
                .requesterId(requesterId)
                .title("제목")
                .content("내용")
                .steps(steps)
                .finalStatus(finalStatus)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return approvalRepository.save(doc);
    }
}
