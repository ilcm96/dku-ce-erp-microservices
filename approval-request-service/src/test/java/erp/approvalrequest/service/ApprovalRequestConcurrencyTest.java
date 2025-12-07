package erp.approvalrequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import erp.approvalrequest.ApprovalRequestIntegrationTestSupport;
import erp.approvalrequest.client.EmployeeClient;
import erp.approvalrequest.client.NotificationClient;
import erp.approvalrequest.domain.ApprovalDocument;
import erp.approvalrequest.repository.ApprovalRepository;
import erp.common.exception.CustomException;
import erp.common.exception.ErrorCode;
import erp.common.security.AuthUtil;
import erp.shared.proto.approval.ApprovalResultStatus;
import erp.shared.proto.approval.StepStatus;

@Tag("concurrency")
@TestPropertySource(properties = {
        "approval.lock.retry.max-attempts=3",
        "approval.lock.retry.backoff-millis=5",
        "processing.retry.max-attempts=1",
        "processing.retry.backoff-millis=1"
})
@Import(ApprovalRequestConcurrencyTest.MockConfig.class)
class ApprovalRequestConcurrencyTest extends ApprovalRequestIntegrationTestSupport {

    @Autowired
    private ApprovalRequestService approvalRequestService;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private NotificationClient notificationClient;

    @Autowired
    private AuthUtil authUtil;

    @AfterEach
    void tearDownMocks() {
        reset(rabbitTemplate, notificationClient, authUtil);
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

    @Test
    @DisplayName("동일 단계 다수 동시 승인 시 한 번만 처리되고 메시지는 한 번만 발행된다")
    void concurrentApproveSameStep() throws Exception {
        // given
        ApprovalDocument doc = saveDocument(
                1L,
                List.of(
                        step(1, 10L, StepStatus.STEP_STATUS_PENDING),
                        step(2, 20L, StepStatus.STEP_STATUS_PENDING)),
                StepStatus.STEP_STATUS_PENDING);

        int threads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();

        // when
        List<CompletableFuture<Void>> futures = IntStream.range(0, threads)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    try {
                        start.await();
                        approvalRequestService.updateResult(
                                doc.getRequestId(), 10L, 1, ApprovalResultStatus.APPROVAL_RESULT_APPROVED);
                    } catch (CustomException e) {
                        errors.incrementAndGet();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        done.countDown();
                    }
                }, executor))
                .toList();

        start.countDown();
        done.await();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        // then
        ApprovalDocument updated = approvalRepository.findByRequestId(doc.getRequestId()).orElseThrow();
        assertThat(updated.getSteps().get(0).getStatus()).isEqualTo(StepStatus.STEP_STATUS_APPROVED);
        assertThat(updated.getSteps().get(1).getStatus()).isEqualTo(StepStatus.STEP_STATUS_PENDING);
        assertThat(updated.getFinalStatus()).isEqualTo(StepStatus.STEP_STATUS_PENDING);

        // RabbitMQ 는 한 번만 발행되어야 한다
        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), org.mockito.ArgumentMatchers.<byte[]>any());
        verify(notificationClient, never()).send(any(), any());
        // 에러가 발생하더라도 서비스가 완료되었음을 보장
        assertThat(errors.get()).isLessThan(threads);
    }

    @Test
    @DisplayName("동일 단계에 승인/반려 요청이 동시에 들어오면 한 결과만 반영되고 알림은 한 번만 발생한다")
    void concurrentApproveAndRejectSameStep() throws Exception {
        // given
        ApprovalDocument doc = saveDocument(
                1L,
                List.of(step(1, 10L, StepStatus.STEP_STATUS_PENDING)),
                StepStatus.STEP_STATUS_PENDING);

        int threads = 40;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger conflicts = new AtomicInteger();

        // when
        List<CompletableFuture<Void>> futures = IntStream.range(0, threads)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    try {
                        start.await();
                        ApprovalResultStatus status =
                                (i % 2 == 0) ? ApprovalResultStatus.APPROVAL_RESULT_APPROVED
                                        : ApprovalResultStatus.APPROVAL_RESULT_REJECTED;
                        approvalRequestService.updateResult(doc.getRequestId(), 10L, 1, status);
                    } catch (CustomException e) {
                        if (e.getErrorCode() == ErrorCode.APPROVAL_PROCESS_INVALID_STATUS
                                || e.getErrorCode() == ErrorCode.APPROVAL_PROCESS_CONFLICT) {
                            conflicts.incrementAndGet();
                        } else {
                            throw new RuntimeException(e);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        done.countDown();
                    }
                }, executor))
                .toList();

        start.countDown();
        done.await();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        // then
        ApprovalDocument updated = approvalRepository.findByRequestId(doc.getRequestId()).orElseThrow();
        assertThat(updated.getFinalStatus())
                .isIn(StepStatus.STEP_STATUS_APPROVED, StepStatus.STEP_STATUS_REJECTED);

        // 알림은 최종 결과 한 번만
        verify(notificationClient, times(1)).send(any(), any());
        // 단일 단계이므로 processing 큐로는 발행되지 않는다
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), org.mockito.ArgumentMatchers.<byte[]>any());
        assertThat(conflicts.get()).isGreaterThan(0);
    }

    @Test
    @DisplayName("다음 단계가 먼저 처리 요청되어도 순서 검증에 걸려 모두 실패한다")
    void concurrentNextStepBeforeCurrent() throws Exception {
        // given
        ApprovalDocument doc = saveDocument(
                1L,
                List.of(
                        step(1, 10L, StepStatus.STEP_STATUS_PENDING),
                        step(2, 20L, StepStatus.STEP_STATUS_PENDING)),
                StepStatus.STEP_STATUS_PENDING);

        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger invalid = new AtomicInteger();

        // when
        List<CompletableFuture<Void>> futures = IntStream.range(0, threads)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    try {
                        start.await();
                        approvalRequestService.updateResult(
                                doc.getRequestId(), 20L, 2, ApprovalResultStatus.APPROVAL_RESULT_APPROVED);
                    } catch (CustomException e) {
                        if (e.getErrorCode() == ErrorCode.APPROVAL_PROCESS_INVALID_STATUS) {
                            invalid.incrementAndGet();
                        } else {
                            throw new RuntimeException(e);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        done.countDown();
                    }
                }, executor))
                .toList();

        start.countDown();
        done.await();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        // then
        ApprovalDocument updated = approvalRepository.findByRequestId(doc.getRequestId()).orElseThrow();
        assertThat(updated.getSteps().get(0).getStatus()).isEqualTo(StepStatus.STEP_STATUS_PENDING);
        assertThat(updated.getSteps().get(1).getStatus()).isEqualTo(StepStatus.STEP_STATUS_PENDING);
        assertThat(updated.getFinalStatus()).isEqualTo(StepStatus.STEP_STATUS_PENDING);

        assertThat(invalid.get()).isEqualTo(threads);
        verify(notificationClient, never()).send(any(), any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), org.mockito.ArgumentMatchers.<byte[]>any());
    }
}
