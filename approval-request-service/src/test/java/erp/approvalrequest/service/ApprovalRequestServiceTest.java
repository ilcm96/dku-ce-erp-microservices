package erp.approvalrequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.google.protobuf.InvalidProtocolBufferException;

import erp.approvalrequest.ApprovalRequestIntegrationTestSupport;
import erp.approvalrequest.client.EmployeeClient;
import erp.approvalrequest.client.NotificationClient;
import erp.approvalrequest.domain.ApprovalDocument;
import erp.approvalrequest.dto.ApprovalCreateRequest;
import erp.approvalrequest.dto.ApprovalResponse;
import erp.approvalrequest.repository.ApprovalRepository;
import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;
import erp.common.security.AuthUtil;
import erp.common.security.Role;
import erp.common.messaging.ApprovalMessagingConstants;
import erp.shared.proto.approval.ApprovalRequest;
import erp.shared.proto.approval.ApprovalResultStatus;
import erp.shared.proto.approval.StepStatus;

@TestPropertySource(properties = {
        "processing.retry.max-attempts=2",
        "processing.retry.backoff-millis=10"
})
@Import(ApprovalRequestServiceTest.MockConfig.class)
class ApprovalRequestServiceTest extends ApprovalRequestIntegrationTestSupport {

    @Autowired
    private ApprovalRequestService approvalRequestService;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private EmployeeClient employeeClient;

    @Autowired
    private NotificationClient notificationClient;

    @Autowired
    private AuthUtil authUtil;

    @AfterEach
    void tearDownMocks() {
        reset(rabbitTemplate, employeeClient, notificationClient, authUtil);
    }

    @TestConfiguration
    static class MockConfig {

        @Bean
        @Primary
        EmployeeClient testEmployeeClient() {
            return Mockito.mock(EmployeeClient.class);
        }

        @Bean
        @Primary
        NotificationClient testNotificationClient() {
            return Mockito.mock(NotificationClient.class);
        }

        @Bean
        @Primary
        AuthUtil testAuthUtil() {
            return Mockito.mock(AuthUtil.class);
        }
    }

    @Nested
    class Create {

        @Test
        void 요청자가_결재를_생성하면_저장하고_processing에_전송한다() {
            // given
            ApprovalCreateRequest request = new ApprovalCreateRequest(
                    "연차 신청", "이틀 연차", List.of(
                            new ApprovalCreateRequest.StepDto(1, 10L),
                            new ApprovalCreateRequest.StepDto(2, 20L)));
            given(authUtil.currentUserId()).willReturn(1L);
            given(employeeClient.findById(1L))
                    .willReturn(new EmployeeClient.EmployeeDto(1L, "req@example.com", "요청자", "개발팀", "사원", Role.EMPLOYEE));
            given(employeeClient.findRole(10L)).willReturn(Role.APPROVER);
            given(employeeClient.findRole(20L)).willReturn(Role.ADMIN);

            // when
            ApprovalResponse response = approvalRequestService.create(request);

            // then: 반환값 검증
            assertThat(response.requestId()).isNotNull();
            assertThat(response.steps()).hasSize(2);
            assertThat(response.finalStatus()).isEqualTo(StepStatus.STEP_STATUS_PENDING);

            // then: DB 상태 검증
            ApprovalDocument saved = approvalRepository.findByRequestId(response.requestId()).orElseThrow();
            assertThat(saved.getRequesterId()).isEqualTo(1L);
            assertThat(saved.getSteps()).hasSize(2);
            assertThat(saved.getSteps())
                    .extracting(ApprovalDocument.StepInfo::getStatus)
                    .containsOnly(StepStatus.STEP_STATUS_PENDING);
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();

            // then: RabbitMQ 발행 검증
            ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
            verify(rabbitTemplate, times(1)).convertAndSend(
                    eq(ApprovalMessagingConstants.EXCHANGE_NAME),
                    eq(ApprovalMessagingConstants.ROUTING_KEY_REQUEST),
                    captor.capture());
            ApprovalRequest sent;
            try {
                sent = ApprovalRequest.parseFrom(captor.getValue());
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
            assertThat(sent.getRequestId()).isEqualTo(saved.getRequestId());
            assertThat(sent.getStepsList()).hasSize(2);
            assertThat(sent.getSteps(0).getStatus()).isEqualTo(StepStatus.STEP_STATUS_PENDING);
        }

        @Test
        void 스텝이_비어있으면_APPROVAL_REQUEST_INVALID_STEP을_던진다() {
            // given
            ApprovalCreateRequest request = new ApprovalCreateRequest("제목", "내용", List.of());
            given(authUtil.currentUserId()).willReturn(1L);
            given(employeeClient.findById(1L))
                    .willReturn(new EmployeeClient.EmployeeDto(1L, "req@example.com", "요청자", "개발팀", "사원", Role.EMPLOYEE));

            // when & then: 예외 검증
            assertThatThrownBy(() -> approvalRequestService.create(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.APPROVAL_REQUEST_INVALID_STEP);
        }

        @Test
        void 스텝_번호가_1부터_시작하지_않거나_중복이면_APPROVAL_REQUEST_INVALID_STEP이다() {
            // given
            ApprovalCreateRequest request = new ApprovalCreateRequest(
                    "제목", "내용",
                    List.of(new ApprovalCreateRequest.StepDto(1, 10L), new ApprovalCreateRequest.StepDto(1, 11L)));
            given(authUtil.currentUserId()).willReturn(1L);
            given(employeeClient.findById(1L))
                    .willReturn(new EmployeeClient.EmployeeDto(1L, "req@example.com", "요청자", "개발팀", "사원", Role.EMPLOYEE));
            given(employeeClient.findRole(any())).willReturn(Role.APPROVER);

            // when & then: 예외 검증
            assertThatThrownBy(() -> approvalRequestService.create(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.APPROVAL_REQUEST_INVALID_STEP);
        }

        @Test
        void 스텝이_역순이면_APPROVAL_REQUEST_INVALID_STEP이다() {
            // given
            ApprovalCreateRequest request = new ApprovalCreateRequest(
                    "제목", "내용",
                    List.of(new ApprovalCreateRequest.StepDto(2, 10L), new ApprovalCreateRequest.StepDto(1, 11L)));
            given(authUtil.currentUserId()).willReturn(1L);
            given(employeeClient.findById(1L))
                    .willReturn(new EmployeeClient.EmployeeDto(1L, "req@example.com", "요청자", "개발팀", "사원", Role.EMPLOYEE));
            given(employeeClient.findRole(any())).willReturn(Role.APPROVER);

            // when & then: 예외 검증
            assertThatThrownBy(() -> approvalRequestService.create(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.APPROVAL_REQUEST_INVALID_STEP);
        }

        @Test
        void 스텝이_건너뛰면_APPROVAL_REQUEST_INVALID_STEP이다() {
            // given
            ApprovalCreateRequest request = new ApprovalCreateRequest(
                    "제목", "내용",
                    List.of(new ApprovalCreateRequest.StepDto(1, 10L), new ApprovalCreateRequest.StepDto(3, 11L)));
            given(authUtil.currentUserId()).willReturn(1L);
            given(employeeClient.findById(1L))
                    .willReturn(new EmployeeClient.EmployeeDto(1L, "req@example.com", "요청자", "개발팀", "사원", Role.EMPLOYEE));
            given(employeeClient.findRole(any())).willReturn(Role.APPROVER);

            // when & then: 예외 검증
            assertThatThrownBy(() -> approvalRequestService.create(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.APPROVAL_REQUEST_INVALID_STEP);
        }

        @Test
        void 요청자와_동일한_approver가_있으면_APPROVAL_SELF_APPROVAL_NOT_ALLOWED를_던진다() {
            // given
            ApprovalCreateRequest request = new ApprovalCreateRequest(
                    "제목", "내용", List.of(new ApprovalCreateRequest.StepDto(1, 1L)));
            given(authUtil.currentUserId()).willReturn(1L);
            given(employeeClient.findById(1L))
                    .willReturn(new EmployeeClient.EmployeeDto(1L, "req@example.com", "요청자", "개발팀", "사원", Role.EMPLOYEE));

            // when & then: 예외 검증
            assertThatThrownBy(() -> approvalRequestService.create(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.APPROVAL_SELF_APPROVAL_NOT_ALLOWED);
        }

        @Test
        void 승인자_역할이_EMPLOYEE이면_APPROVAL_APPROVER_NOT_ELIGIBLE을_던진다() {
            // given
            ApprovalCreateRequest request = new ApprovalCreateRequest(
                    "제목", "내용", List.of(new ApprovalCreateRequest.StepDto(1, 10L)));
            given(authUtil.currentUserId()).willReturn(1L);
            given(employeeClient.findById(1L))
                    .willReturn(new EmployeeClient.EmployeeDto(1L, "req@example.com", "요청자", "개발팀", "사원", Role.EMPLOYEE));
            given(employeeClient.findRole(10L)).willReturn(Role.EMPLOYEE);

            // when & then: 예외 검증
            assertThatThrownBy(() -> approvalRequestService.create(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.APPROVAL_APPROVER_NOT_ELIGIBLE);
        }
    }

    @Nested
    class ListForCurrentUser {

        @Test
        void ADMIN이면_모든_문서를_조회한다() {
            // given
            saveDocument(1L, List.of(step(1, 10L, StepStatus.STEP_STATUS_PENDING)), StepStatus.STEP_STATUS_PENDING);
            saveDocument(2L, List.of(step(1, 20L, StepStatus.STEP_STATUS_PENDING)), StepStatus.STEP_STATUS_PENDING);
            given(authUtil.currentUserId()).willReturn(99L);
            given(authUtil.hasRole(Role.ADMIN)).willReturn(true);

            // when
            List<ApprovalResponse> responses = approvalRequestService.listForCurrentUser();

            // then: 반환값 검증
            assertThat(responses).hasSize(2);
        }

        @Test
        void 요청자_혹은_결재자로_참여한_문서만_조회한다() {
            // given
            saveDocument(1L, List.of(step(1, 10L, StepStatus.STEP_STATUS_PENDING)), StepStatus.STEP_STATUS_PENDING);
            saveDocument(3L, List.of(step(1, 1L, StepStatus.STEP_STATUS_PENDING)), StepStatus.STEP_STATUS_PENDING);
            saveDocument(4L, List.of(step(1, 5L, StepStatus.STEP_STATUS_PENDING)), StepStatus.STEP_STATUS_PENDING);
            given(authUtil.currentUserId()).willReturn(1L);
            given(authUtil.hasRole(Role.ADMIN)).willReturn(false);

            // when
            List<ApprovalResponse> responses = approvalRequestService.listForCurrentUser();

            // then: 반환값 검증
            assertThat(responses)
                    .extracting(ApprovalResponse::requesterId)
                    .containsExactlyInAnyOrder(1L, 3L);
        }
    }

    @Nested
    class FindOne {

        @Test
        void 요청자는_자신의_문서를_조회할_수_있다() {
            // given
            ApprovalDocument doc = saveDocument(7L, List.of(step(1, 10L, StepStatus.STEP_STATUS_PENDING)), StepStatus.STEP_STATUS_PENDING);
            given(authUtil.currentUserId()).willReturn(7L);
            given(authUtil.hasRole(Role.ADMIN)).willReturn(false);

            // when
            ApprovalResponse response = approvalRequestService.findOne(doc.getRequestId());

            // then: 반환값 검증
            assertThat(response.requestId()).isEqualTo(doc.getRequestId());
        }

        @Test
        void 결재자도_해당_문서를_조회할_수_있다() {
            // given
            ApprovalDocument doc = saveDocument(7L, List.of(step(1, 8L, StepStatus.STEP_STATUS_PENDING)), StepStatus.STEP_STATUS_PENDING);
            given(authUtil.currentUserId()).willReturn(8L);
            given(authUtil.hasRole(Role.ADMIN)).willReturn(false);

            // when
            ApprovalResponse response = approvalRequestService.findOne(doc.getRequestId());

            // then: 반환값 검증
            assertThat(response.requestId()).isEqualTo(doc.getRequestId());
        }

        @Test
        void 관련되지_않은_사용자는_FORBIDDEN을_던진다() {
            // given
            ApprovalDocument doc = saveDocument(7L, List.of(step(1, 8L, StepStatus.STEP_STATUS_PENDING)), StepStatus.STEP_STATUS_PENDING);
            given(authUtil.currentUserId()).willReturn(9L);
            given(authUtil.hasRole(Role.ADMIN)).willReturn(false);

            // when & then: 예외 검증
            assertThatThrownBy(() -> approvalRequestService.findOne(doc.getRequestId()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        void 존재하지_않으면_APPORVAL_REQUEST_NOT_FOUND를_던진다() {
            // given
            given(authUtil.currentUserId()).willReturn(1L);
            given(authUtil.hasRole(Role.ADMIN)).willReturn(true);

            // when & then: 예외 검증
            assertThatThrownBy(() -> approvalRequestService.findOne(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.APPROVAL_REQUEST_NOT_FOUND);
        }
    }

    @Nested
    class ResendPending {

        @Test
        void pending_스텝이_있으면_processing에_재전송한다() {
            // given
            ApprovalDocument doc = saveDocument(1L, List.of(step(1, 10L, StepStatus.STEP_STATUS_PENDING)), StepStatus.STEP_STATUS_PENDING);
            given(authUtil.currentUserId()).willReturn(1L);
            given(authUtil.hasRole(Role.ADMIN)).willReturn(false);

            // when
            approvalRequestService.resendPending(doc.getRequestId());

            // then: 메시지 발행 검증
            verify(rabbitTemplate, times(1)).convertAndSend(
                    eq(ApprovalMessagingConstants.EXCHANGE_NAME),
                    eq(ApprovalMessagingConstants.ROUTING_KEY_REQUEST),
                    any(byte[].class));
        }

        @Test
        void pending_스텝이_없으면_gRPC를_호출하지_않는다() {
            // given
            ApprovalDocument doc = saveDocument(1L,
                    List.of(step(1, 10L, StepStatus.STEP_STATUS_APPROVED)),
                    StepStatus.STEP_STATUS_APPROVED);
            given(authUtil.currentUserId()).willReturn(1L);
            given(authUtil.hasRole(Role.ADMIN)).willReturn(false);

            // when
            approvalRequestService.resendPending(doc.getRequestId());

            // then: 메시지 발행 없음
            verifyNoInteractions(rabbitTemplate);
        }

        @Test
        void 접근_권한이_없으면_FORBIDDEN을_던진다() {
            // given
            ApprovalDocument doc = saveDocument(1L, List.of(step(1, 10L, StepStatus.STEP_STATUS_PENDING)), StepStatus.STEP_STATUS_PENDING);
            given(authUtil.currentUserId()).willReturn(99L);
            given(authUtil.hasRole(Role.ADMIN)).willReturn(false);

            // when & then: 예외 검증
            assertThatThrownBy(() -> approvalRequestService.resendPending(doc.getRequestId()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }
    }

    @Nested
    class UpdateResult {

        @Test
        void 중간_단계를_승인하면_해당_스텝만_APPROVED가_되고_다음_스텝을_요청한다() {
            // given
            ApprovalDocument doc = saveDocument(1L,
                    List.of(
                            step(1, 10L, StepStatus.STEP_STATUS_PENDING),
                            step(2, 20L, StepStatus.STEP_STATUS_PENDING)),
                    StepStatus.STEP_STATUS_PENDING);

            // when
            approvalRequestService.updateResult(
                    doc.getRequestId(), 10L, 1, ApprovalResultStatus.APPROVAL_RESULT_APPROVED);

            // then: 반환값/DB 상태 검증
            ApprovalDocument updated = approvalRepository.findByRequestId(doc.getRequestId()).orElseThrow();
            assertThat(updated.getSteps().get(0).getStatus()).isEqualTo(StepStatus.STEP_STATUS_APPROVED);
            assertThat(updated.getSteps().get(1).getStatus()).isEqualTo(StepStatus.STEP_STATUS_PENDING);
            assertThat(updated.getFinalStatus()).isEqualTo(StepStatus.STEP_STATUS_PENDING);

            // then: 다음 단계 메시지 전송
            verify(rabbitTemplate, times(1)).convertAndSend(
                    eq(ApprovalMessagingConstants.EXCHANGE_NAME),
                    eq(ApprovalMessagingConstants.ROUTING_KEY_REQUEST),
                    any(byte[].class));
        }

        @Test
        void 최종_승인시_finalStatus_APPROVED로_설정하고_알림을_보낸다() {
            // given
            ApprovalDocument doc = saveDocument(1L,
                    List.of(
                            step(1, 10L, StepStatus.STEP_STATUS_APPROVED),
                            step(2, 20L, StepStatus.STEP_STATUS_PENDING)),
                    StepStatus.STEP_STATUS_PENDING);

            // when
            approvalRequestService.updateResult(
                    doc.getRequestId(), 20L, 2, ApprovalResultStatus.APPROVAL_RESULT_APPROVED);

            // then: DB 상태 검증
            ApprovalDocument updated = approvalRepository.findByRequestId(doc.getRequestId()).orElseThrow();
            assertThat(updated.getFinalStatus()).isEqualTo(StepStatus.STEP_STATUS_APPROVED);
            assertThat(updated.getSteps().get(1).getStatus()).isEqualTo(StepStatus.STEP_STATUS_APPROVED);

            // then: 알림 및 gRPC 호출 검증
            verify(notificationClient, times(1)).send(eq(1L), eq(String.format(
                    "{\"requestId\":%d,\"result\":\"approved\",\"finalResult\":\"approved\"}",
                    doc.getRequestId())));
            verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(byte[].class));
        }

        @Test
        void 반려시_finalStatus_REJECTED로_설정하고_rejectedBy를_포함해_알림한다() {
            // given
            ApprovalDocument doc = saveDocument(1L,
                    List.of(step(1, 10L, StepStatus.STEP_STATUS_PENDING)),
                    StepStatus.STEP_STATUS_PENDING);

            // when
            approvalRequestService.updateResult(
                    doc.getRequestId(), 10L, 1, ApprovalResultStatus.APPROVAL_RESULT_REJECTED);

            // then: DB 상태 검증
            ApprovalDocument updated = approvalRepository.findByRequestId(doc.getRequestId()).orElseThrow();
            assertThat(updated.getFinalStatus()).isEqualTo(StepStatus.STEP_STATUS_REJECTED);
            assertThat(updated.getSteps().get(0).getStatus()).isEqualTo(StepStatus.STEP_STATUS_REJECTED);

            // then: 알림 payload 검증
            verify(notificationClient, times(1)).send(eq(1L), eq(String.format(
                    "{\"requestId\":%d,\"result\":\"rejected\",\"rejectedBy\":%d,\"finalResult\":\"rejected\"}",
                    doc.getRequestId(), 10L)));
            verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(byte[].class));
        }

        @Test
        void 동일한_승인_결과는_idempotent하게_무시한다() {
            // given
            ApprovalDocument doc = saveDocument(1L,
                    List.of(
                            step(1, 10L, StepStatus.STEP_STATUS_APPROVED),
                            step(2, 20L, StepStatus.STEP_STATUS_PENDING)),
                    StepStatus.STEP_STATUS_PENDING);

            // when
            approvalRequestService.updateResult(
                    doc.getRequestId(), 10L, 1, ApprovalResultStatus.APPROVAL_RESULT_APPROVED);

            // then: 상태 변경 없음
            ApprovalDocument updated = approvalRepository.findByRequestId(doc.getRequestId()).orElseThrow();
            assertThat(updated.getSteps().get(0).getStatus()).isEqualTo(StepStatus.STEP_STATUS_APPROVED);
            verifyNoInteractions(notificationClient, rabbitTemplate);
        }

        @Test
        void 이미_승인된_스텝에_다른_상태가_오면_APPROVAL_PROCESS_INVALID_STATUS() {
            // given
            ApprovalDocument doc = saveDocument(1L,
                    List.of(
                            step(1, 10L, StepStatus.STEP_STATUS_APPROVED),
                            step(2, 20L, StepStatus.STEP_STATUS_PENDING)),
                    StepStatus.STEP_STATUS_PENDING);

            // when & then: 예외 검증
            assertThatThrownBy(() -> approvalRequestService.updateResult(
                            doc.getRequestId(), 10L, 1, ApprovalResultStatus.APPROVAL_RESULT_REJECTED))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.APPROVAL_PROCESS_INVALID_STATUS);
        }

        @Test
        void 첫번째_pending이_아닌_스텝을_처리하면_APPROVAL_PROCESS_INVALID_STATUS() {
            // given
            ApprovalDocument doc = saveDocument(1L,
                    List.of(
                            step(1, 10L, StepStatus.STEP_STATUS_PENDING),
                            step(2, 20L, StepStatus.STEP_STATUS_PENDING)),
                    StepStatus.STEP_STATUS_PENDING);

            // when & then: 예외 검증
            assertThatThrownBy(() -> approvalRequestService.updateResult(
                            doc.getRequestId(), 20L, 2, ApprovalResultStatus.APPROVAL_RESULT_APPROVED))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.APPROVAL_PROCESS_INVALID_STATUS);
        }

        @Test
        void 대상_스텝이_없으면_APPROVAL_PROCESS_NOT_FOUND를_던진다() {
            // given
            ApprovalDocument doc = saveDocument(1L,
                    List.of(step(1, 10L, StepStatus.STEP_STATUS_PENDING)),
                    StepStatus.STEP_STATUS_PENDING);

            // when & then: 예외 검증
            assertThatThrownBy(() -> approvalRequestService.updateResult(
                            doc.getRequestId(), 99L, 2, ApprovalResultStatus.APPROVAL_RESULT_APPROVED))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.APPROVAL_PROCESS_NOT_FOUND);
        }
    }

    @Nested
    class CallProcessingWithRetry {

        @Test
        void 첫_시도는_실패하지만_재시도에_성공하면_예외없이_종료한다() {
            // given
            ApprovalCreateRequest request = new ApprovalCreateRequest(
                    "재시도", "내용", List.of(new ApprovalCreateRequest.StepDto(1, 10L)));
            given(authUtil.currentUserId()).willReturn(1L);
            given(employeeClient.findById(1L))
                    .willReturn(new EmployeeClient.EmployeeDto(1L, "req@example.com", "요청자", "개발팀", "사원", Role.EMPLOYEE));
            given(employeeClient.findRole(10L)).willReturn(Role.APPROVER);
            doThrow(new RuntimeException("first fail"))
                    .doNothing()
                    .when(rabbitTemplate)
                    .convertAndSend(eq(ApprovalMessagingConstants.EXCHANGE_NAME),
                            eq(ApprovalMessagingConstants.ROUTING_KEY_REQUEST), any(byte[].class));

            // when
            approvalRequestService.create(request);

            // then: 재시도 횟수 검증
            verify(rabbitTemplate, times(2)).convertAndSend(
                    eq(ApprovalMessagingConstants.EXCHANGE_NAME),
                    eq(ApprovalMessagingConstants.ROUTING_KEY_REQUEST),
                    any(byte[].class));
        }

        @Test
        void 모든_재시도에_실패하면_예외를_전파한다() {
            // given
            ApprovalCreateRequest request = new ApprovalCreateRequest(
                    "재시도 실패", "내용", List.of(new ApprovalCreateRequest.StepDto(1, 10L)));
            given(authUtil.currentUserId()).willReturn(1L);
            given(employeeClient.findById(1L))
                    .willReturn(new EmployeeClient.EmployeeDto(1L, "req@example.com", "요청자", "개발팀", "사원", Role.EMPLOYEE));
            given(employeeClient.findRole(10L)).willReturn(Role.APPROVER);
            doThrow(new RuntimeException("fail1"))
                    .doThrow(new RuntimeException("fail2"))
                    .when(rabbitTemplate)
                    .convertAndSend(eq(ApprovalMessagingConstants.EXCHANGE_NAME),
                            eq(ApprovalMessagingConstants.ROUTING_KEY_REQUEST), any(byte[].class));

            // when & then: 예외 검증
            assertThatThrownBy(() -> approvalRequestService.create(request))
                    .isInstanceOf(RuntimeException.class);
            verify(rabbitTemplate, times(2)).convertAndSend(
                    eq(ApprovalMessagingConstants.EXCHANGE_NAME),
                    eq(ApprovalMessagingConstants.ROUTING_KEY_REQUEST),
                    any(byte[].class));
        }
    }
}
