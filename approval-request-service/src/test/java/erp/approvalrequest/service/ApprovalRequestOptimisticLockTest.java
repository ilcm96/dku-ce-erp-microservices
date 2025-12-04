package erp.approvalrequest.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import erp.approvalrequest.client.EmployeeClient;
import erp.approvalrequest.client.NotificationClient;
import erp.approvalrequest.domain.ApprovalDocument;
import erp.approvalrequest.repository.ApprovalRepository;
import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;
import erp.common.security.AuthUtil;
import erp.shared.proto.approval.ApprovalResultStatus;
import erp.shared.proto.approval.StepStatus;

@ExtendWith(MockitoExtension.class)
class ApprovalRequestOptimisticLockTest {

    @Mock
    private ApprovalRepository approvalRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private AuthUtil authUtil;
    @Mock
    private RequestIdGenerator requestIdGenerator;
    @Mock
    private EmployeeClient employeeClient;
    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private ApprovalRequestService approvalRequestService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(approvalRequestService, "lockMaxAttempts", 2);
        ReflectionTestUtils.setField(approvalRequestService, "lockBackoffMillis", 1L);
    }

    @Test
    void 낙관적락_충돌시_재시도후_성공하면_예외없이_종료한다() {
        // given
        long requestId = 1L;
        ApprovalDocument pending = document(requestId);
        ApprovalDocument pendingAgain = document(requestId);

        when(approvalRepository.findByRequestId(requestId))
                .thenReturn(java.util.Optional.of(pending))
                .thenReturn(java.util.Optional.of(pendingAgain));

        when(approvalRepository.save(any(ApprovalDocument.class)))
                .thenThrow(new OptimisticLockingFailureException("conflict"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when & then
        assertThatCode(() ->
                approvalRequestService.updateResult(requestId, 10L, 1, ApprovalResultStatus.APPROVAL_RESULT_APPROVED))
                .doesNotThrowAnyException();

        verify(approvalRepository, times(2)).save(any(ApprovalDocument.class));
        verify(notificationClient, times(1)).send(any(), any());
    }

    @Test
    void 낙관적락이_계속되면_CONFLICT_예외를_던진다() {
        // given
        long requestId = 2L;
        ReflectionTestUtils.setField(approvalRequestService, "lockMaxAttempts", 1);
        when(approvalRepository.findByRequestId(requestId)).thenReturn(java.util.Optional.of(document(requestId)));
        when(approvalRepository.save(any(ApprovalDocument.class)))
                .thenThrow(new OptimisticLockingFailureException("conflict"));

        // when
        Throwable thrown = catchThrowable(() -> approvalRequestService.updateResult(
                requestId, 10L, 1, ApprovalResultStatus.APPROVAL_RESULT_APPROVED));

        // then
        assertThatThrownBy(() -> { throw thrown; })
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPROVAL_PROCESS_CONFLICT);
        verify(approvalRepository, times(1)).save(any(ApprovalDocument.class));
    }

    private ApprovalDocument document(long requestId) {
        return ApprovalDocument.builder()
                .requestId(requestId)
                .requesterId(1L)
                .title("제목")
                .content("내용")
                .steps(List.of(ApprovalDocument.StepInfo.builder()
                        .step(1)
                        .approverId(10L)
                        .status(StepStatus.STEP_STATUS_PENDING)
                        .updatedAt(Instant.now())
                        .build()))
                .finalStatus(StepStatus.STEP_STATUS_PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
