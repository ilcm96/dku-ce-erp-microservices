package erp.approvalrequest.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import erp.approvalprocessing.ApprovalProcessingServiceApplication;
import erp.approvalprocessing.service.ApprovalQueueService;
import erp.approvalrequest.ApprovalRequestServiceApplication;
import erp.approvalrequest.dto.ApprovalCreateRequest;
import erp.approvalrequest.dto.ApprovalResponse;
import erp.approvalrequest.support.TestJwtFactory;
import erp.approvalrequest.repository.ApprovalRepository;
import erp.common.exception.ErrorResponse;
import erp.common.security.Role;
import erp.employee.EmployeeServiceApplication;
import erp.employee.dto.EmployeeRequest;
import erp.employee.dto.EmployeeResponse;
import erp.notification.NotificationServiceApplication;
import erp.shared.proto.approval.ApprovalResultStatus;
import erp.shared.proto.approval.StepStatus;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Tag("e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApprovalFlowE2ETest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8");

    @Container
    static final MongoDBContainer mongo = new MongoDBContainer("mongo:8.2");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ConfigurableApplicationContext employeeContext;
    private ConfigurableApplicationContext notificationContext;
    private ConfigurableApplicationContext processingContext;
    private ConfigurableApplicationContext approvalRequestContext;

    private ApprovalRepository approvalRepository;
    private ApprovalQueueService approvalQueueService;

    private final Set<Integer> allocatedPorts = new HashSet<>();

    private int employeePort;
    private int notificationPort;
    private int processingPort;
    private int approvalPort;
    private int processingGrpcPort;
    private int approvalGrpcPort;

    private Long requesterId;
    private Long approver1Id;
    private Long approver2Id;
    private String requesterJwt;
    private String approver1Jwt;
    private String approver2Jwt;

    @BeforeAll
    void setUpEnvironment() {
        mongo.start();
        mysql.start();

        String mongoUri = String.format("mongodb://%s:%d/approval-e2e", mongo.getHost(), mongo.getMappedPort(27017));

        employeePort = findFreePort();
        notificationPort = findFreePort();
        processingPort = findFreePort();
        approvalPort = findFreePort();
        processingGrpcPort = findFreePort();
        approvalGrpcPort = findFreePort();

        System.out.printf("[E2E] ports - employee:%d, notification:%d, processing:%d, approval:%d, grpcProcessing:%d, grpcApproval:%d%n",
                employeePort, notificationPort, processingPort, approvalPort, processingGrpcPort, approvalGrpcPort);

        Map<String, Object> employeeProps = new HashMap<>();
        employeeProps.put("server.port", employeePort);
        employeeProps.put("spring.datasource.url", mysql.getJdbcUrl());
        employeeProps.put("spring.datasource.username", mysql.getUsername());
        employeeProps.put("spring.datasource.password", mysql.getPassword());
        employeeProps.put("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        employeeProps.put("spring.jpa.hibernate.ddl-auto", "create-drop");
        employeeProps.put("security.jwt.secret", TestJwtFactory.SECRET);
        employeeProps.put("security.jwt.expiration-minutes", 60);
        employeeProps.put("employee.admin.seed.enabled", false);
        employeeProps.put("spring.autoconfigure.exclude", "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration");

        employeeContext = new SpringApplicationBuilder(EmployeeServiceApplication.class)
                .environment(environmentWithOverrides(employeeProps))
                .profiles("e2e")
                .properties(employeeProps)
                .run();

        Map<String, Object> notificationProps = new HashMap<>();
        notificationProps.put("server.port", notificationPort);
        notificationProps.put("security.jwt.secret", TestJwtFactory.SECRET);
        notificationProps.put("spring.autoconfigure.exclude", "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration");

        notificationContext = new SpringApplicationBuilder(NotificationServiceApplication.class)
                .environment(environmentWithOverrides(notificationProps))
                .profiles("e2e")
                .properties(notificationProps)
                .run();

        Map<String, Object> processingProps = new HashMap<>();
        processingProps.put("server.port", processingPort);
        processingProps.put("spring.grpc.server.port", processingGrpcPort);
        processingProps.put("spring.grpc.client.channels.approval-request.address", "localhost:" + approvalGrpcPort);
        processingProps.put("approval-request.retry.max-attempts", 2);
        processingProps.put("approval-request.retry.backoff-millis", 50);
        processingProps.put("security.jwt.secret", TestJwtFactory.SECRET);
        processingProps.put("spring.autoconfigure.exclude", "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration");

        processingContext = new SpringApplicationBuilder(ApprovalProcessingServiceApplication.class)
                .environment(environmentWithOverrides(processingProps))
                .profiles("e2e")
                .properties(processingProps)
                .run();

        Map<String, Object> approvalProps = new HashMap<>();
        approvalProps.put("server.port", approvalPort);
        approvalProps.put("spring.data.mongodb.uri", mongoUri);
        approvalProps.put("spring.grpc.server.port", approvalGrpcPort);
        approvalProps.put("spring.grpc.client.channels.approval-processing.address", "localhost:" + processingGrpcPort);
        approvalProps.put("employee.base-url", "http://localhost:" + employeePort);
        approvalProps.put("notification.base-url", "http://localhost:" + notificationPort);
        approvalProps.put("notification.path", "/internal/notifications");
        approvalProps.put("processing.retry.max-attempts", 2);
        approvalProps.put("processing.retry.backoff-millis", 50);
        approvalProps.put("notification.retry.max-attempts", 2);
        approvalProps.put("notification.retry.backoff-millis", 50);
        approvalProps.put("security.jwt.secret", TestJwtFactory.SECRET);
        approvalProps.put("spring.autoconfigure.exclude", "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration");

        approvalRequestContext = new SpringApplicationBuilder(ApprovalRequestServiceApplication.class)
                .environment(environmentWithOverrides(approvalProps))
                .profiles("e2e")
                .properties(approvalProps)
                .run();

        approvalRepository = approvalRequestContext.getBean(ApprovalRepository.class);
        approvalQueueService = processingContext.getBean(ApprovalQueueService.class);

        seedEmployees();
    }

    @AfterEach
    void cleanState() {
        approvalRepository.deleteAll();
        clearQueue(approver1Id);
        clearQueue(approver2Id);
    }

    @AfterAll
    void tearDown() {
        closeQuietly(approvalRequestContext);
        closeQuietly(processingContext);
        closeQuietly(notificationContext);
        closeQuietly(employeeContext);
        mongo.stop();
        mysql.stop();
    }

    @Test
    void 결재_두단계_승인_알림까지_수신한다() throws Exception {
        // given
        ApprovalCreateRequest request = new ApprovalCreateRequest(
                "휴가 신청", "이틀 휴가 요청",
                List.of(new ApprovalCreateRequest.StepDto(1, approver1Id),
                        new ApprovalCreateRequest.StepDto(2, approver2Id)));

        ApprovalResponse created = createApproval(requesterId, Role.EMPLOYEE, request);
        Long requestId = created.requestId();

        // when
        CompletableFuture<String> notification = connectWebSocket(requesterJwt);

        approve(approver1Id, Role.APPROVER, approver1Id, requestId, ApprovalResultStatus.APPROVAL_RESULT_APPROVED);

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(approvalQueueService.getQueue(approver2Id)).hasSize(1));

        approve(approver2Id, Role.APPROVER, approver2Id, requestId, ApprovalResultStatus.APPROVAL_RESULT_APPROVED);

        // then: 알림 수신 검증
        String payload = notification.get(3, TimeUnit.SECONDS);
        assertThat(payload).contains("\"requestId\":" + requestId)
                .contains("\"finalResult\":\"approved\"");

        ApprovalResponse fetched = getApproval(requesterId, Role.EMPLOYEE, requestId);
        assertThat(fetched.finalStatus()).isEqualTo(StepStatus.STEP_STATUS_APPROVED);
        assertThat(fetched.steps()).allSatisfy(step ->
                assertThat(step.status()).isEqualTo(StepStatus.STEP_STATUS_APPROVED));
        assertThat(approvalQueueService.getQueue(approver2Id)).isEmpty();
    }

    @Test
    void 단일단계_반려시_최종거부_알림이_전송된다() throws Exception {
        // given
        ApprovalCreateRequest request = new ApprovalCreateRequest(
                "지출 결의", "법인카드 영수증 첨부",
                List.of(new ApprovalCreateRequest.StepDto(1, approver1Id)));

        ApprovalResponse created = createApproval(requesterId, Role.EMPLOYEE, request);
        Long requestId = created.requestId();

        CompletableFuture<String> notification = connectWebSocket(requesterJwt);

        // when
        approve(approver1Id, Role.APPROVER, approver1Id, requestId, ApprovalResultStatus.APPROVAL_RESULT_REJECTED);

        // then
        String payload = notification.get(3, TimeUnit.SECONDS);
        assertThat(payload).contains("\"result\":\"rejected\"")
                .contains("\"rejectedBy\":" + approver1Id)
                .contains("\"finalResult\":\"rejected\"");

        ApprovalResponse fetched = getApproval(requesterId, Role.EMPLOYEE, requestId);
        assertThat(fetched.finalStatus()).isEqualTo(StepStatus.STEP_STATUS_REJECTED);
        assertThat(fetched.steps()).first()
                .extracting(ApprovalResponse.StepResponse::status)
                .isEqualTo(StepStatus.STEP_STATUS_REJECTED);
        assertThat(approvalQueueService.getQueue(approver1Id)).isEmpty();
    }

    @Test
    void 단계번호가_역순이면_BAD_REQUEST를_반환한다() {
        // given
        ApprovalCreateRequest invalid = new ApprovalCreateRequest(
                "역순 단계", "잘못된 단계 입력",
                List.of(new ApprovalCreateRequest.StepDto(1, approver1Id),
                        new ApprovalCreateRequest.StepDto(3, approver2Id)));

        // when & then: 예외 검증
        assertThatThrownBy(() -> createApproval(requesterId, Role.EMPLOYEE, invalid))
                .isInstanceOf(RestClientResponseException.class)
                .satisfies(ex -> {
                    RestClientResponseException r = (RestClientResponseException) ex;
                    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    ErrorResponse error = parseError(r);
                    assertThat(error.errorCodeName()).isEqualTo("APPROVAL_REQUEST_INVALID_STEP");
                });
    }

    @Test
    void 다른_승인자가_처리하면_NOT_FOUND와_대기열_유지가_발생한다() {
        // given
        ApprovalCreateRequest request = new ApprovalCreateRequest(
                "단일 승인", "승인 대상",
                List.of(new ApprovalCreateRequest.StepDto(1, approver1Id)));
        ApprovalResponse created = createApproval(requesterId, Role.EMPLOYEE, request);
        Long requestId = created.requestId();

        // when & then: 예외 검증
        assertThatThrownBy(() -> approve(approver2Id, Role.APPROVER, approver2Id, requestId, ApprovalResultStatus.APPROVAL_RESULT_APPROVED))
                .isInstanceOf(RestClientResponseException.class)
                .satisfies(ex -> {
                    RestClientResponseException r = (RestClientResponseException) ex;
                    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    ErrorResponse error = parseError(r);
                    assertThat(error.errorCodeName()).isEqualTo("APPROVAL_PROCESS_NOT_FOUND");
                });

        // then: 대기열이 유지됨을 확인
        assertThat(approvalQueueService.getQueue(approver1Id)).hasSize(1);

        // cleanup: 올바른 승인으로 대기열 정리
        approve(approver1Id, Role.APPROVER, approver1Id, requestId, ApprovalResultStatus.APPROVAL_RESULT_APPROVED);
        assertThat(approvalQueueService.getQueue(approver1Id)).isEmpty();
    }

    private ConfigurableEnvironment environmentWithOverrides(Map<String, Object> overrides) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("e2e-overrides", overrides));
        return environment;
    }

    private int findFreePort() {
        while (true) {
            try (ServerSocket socket = new ServerSocket(0)) {
                socket.setReuseAddress(true);
                int port = socket.getLocalPort();
                if (port >= 20000 && allocatedPorts.add(port)) {
                    return port;
                }
            } catch (Exception e) {
                throw new IllegalStateException("사용 가능한 포트를 찾지 못했습니다.", e);
            }
        }
    }

    private void seedEmployees() {
        RestClient adminClient = restClient(employeePort, 1L, Role.ADMIN);

        createEmployee(adminClient, new EmployeeRequest(
                "admin@example.com", "관리자", "경영지원", "실장", Role.ADMIN, "password123!"));

        EmployeeResponse requester = createEmployee(adminClient, new EmployeeRequest(
                "requester@example.com", "요청자", "개발팀", "주임", Role.EMPLOYEE, "password123!"));
        requesterId = requester.id();
        requesterJwt = TestJwtFactory.createToken(requesterId, List.of(Role.EMPLOYEE));

        EmployeeResponse approver1 = createEmployee(adminClient, new EmployeeRequest(
                "approver1@example.com", "승인자1", "개발팀", "리드", Role.APPROVER, "password123!"));
        approver1Id = approver1.id();
        approver1Jwt = TestJwtFactory.createToken(approver1Id, List.of(Role.APPROVER));

        EmployeeResponse approver2 = createEmployee(adminClient, new EmployeeRequest(
                "approver2@example.com", "승인자2", "개발팀", "매니저", Role.APPROVER, "password123!"));
        approver2Id = approver2.id();
        approver2Jwt = TestJwtFactory.createToken(approver2Id, List.of(Role.APPROVER));
    }

    private ApprovalResponse createApproval(Long userId, Role role, ApprovalCreateRequest request) {
        return restClient(approvalPort, userId, role)
                .post()
                .uri("/approvals")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ApprovalResponse.class);
    }

    private ApprovalResponse getApproval(Long userId, Role role, Long requestId) {
        return restClient(approvalPort, userId, role)
                .get()
                .uri("/approvals/{id}", requestId)
                .retrieve()
                .body(ApprovalResponse.class);
    }

    private void approve(Long userId, Role role, Long approverId, Long requestId, ApprovalResultStatus status) {
        restClient(processingPort, userId, role)
                .post()
                .uri("/process/{approverId}/{requestId}", approverId, requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new StatusRequest(status))
                .retrieve()
                .toBodilessEntity();
    }

    private EmployeeResponse createEmployee(RestClient adminClient, EmployeeRequest request) {
        return adminClient.post()
                .uri("/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(EmployeeResponse.class);
    }

    private RestClient restClient(int port, Long userId, Role role) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("X-User-Id", String.valueOf(userId))
                .defaultHeader("X-User-Roles", role.name())
                .build();
    }

    private CompletableFuture<String> connectWebSocket(String token) {
        CompletableFuture<String> future = new CompletableFuture<>();
        HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .buildAsync(URI.create("ws://localhost:" + notificationPort + "/ws?token=" + token),
                        new WebSocket.Listener() {
                            @Override
                            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                                future.complete(data.toString());
                                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                                return CompletableFuture.completedStage(null);
                            }

                            @Override
                            public void onError(WebSocket webSocket, Throwable error) {
                                future.completeExceptionally(error);
                            }
                        });
        return future;
    }

    private void clearQueue(Long approverId) {
        approvalQueueService.getQueue(approverId)
                .forEach(req -> approvalQueueService.remove(approverId, req.getRequestId()));
    }

    private void closeQuietly(ConfigurableApplicationContext context) {
        if (context != null) {
            context.close();
        }
    }

    private ErrorResponse parseError(RestClientResponseException exception) {
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(exception.getResponseBodyAsString());
            return new ErrorResponse(node.get("errorCodeName").asText(), node.get("errorMessage").asText());
        } catch (Exception e) {
            throw new IllegalStateException("에러 응답 파싱 실패", e);
        }
    }

    private record StatusRequest(ApprovalResultStatus status) {}
}
