<current_status></current_status>

<project_guidelines>

# 사용 언어

- 사고 과정은 상관 없으나, 최종 답변은 **한국어**로 작성하세요.
- 주석의 경우 기존의 스타일을 따르거나, 사용자의 프롬프트에서 지정된 언어를 따르세요.

# 질문 모드

- 질문 모드는 사용자가 `/ask` 로 시작하는 프롬프트를 입력하면 활성화합니다.
- 질문 모드에서는 사용자의 프롬프트를 보고 필요한 경우 프로젝트의 파일을 탐색하며 풍부한 컨텍스트를 얻고 이를 바탕으로 질문에 답변합니다.
- 현재 approvals 상태와 관련 없이 그 어떤 경우에도 **프로젝트 파일을 수정하지 않습니다**.
- 코드에 변경 사항을 반영하고 싶은 경우, 수정하는 대신 반영 예시를 텍스트로 제시합니다.
- 프로젝트 파일에 변경을 가하지 않는 명령어의 경우에 제한적으로 실행할 수 있습니다.

# 계획 모드

- 계획 모드는 사용자가 `/plan` 으로 시작하는 프롬프트를 입력하면 활성화합니다.
- 계획 모드에서는 사용자의 프롬프트를 보고 **반드시** 프로젝트의 파일을 탐색하며 풍부한 컨텍스트를 얻고 이를 바탕으로 계획을 작성합니다.
- 본 계획은 `update_plan` 과 같은 내장 도구 사용, Todo-List 작성이 아닌 일반적인 텍스트로 계획을 출력하면 됩니다.

# 디렉토리 구조 탐색 / 파일 탐색

- tree 명령어를 사용하여 빠르게 디렉토리 구조를 파악하세요.
- 파일 탐색은 기본 도구에 더해 `ast-grep 대 ripgrep` 섹션을 참고하여 적절한 도구를 선택하세요.

# Docker 관련

- docker 명령어를 사용했을때 `Cannot connect to the Docker daemon at unix:///Users/yunseongmin/.orbstack/run/docker.sock. Is the docker daemon running?` 오류가 발생한다면, `open -a OrbStack` 명령어를 사용하여 OrbStack을 실행한 후 다시 시도하세요.

# DB 관련

- MySQL의 경우 mysql:8 이미지를 사용하세요.
- MongoDB의 경우 mongo:8.2 이미지를 사용하세요.

# 주석 관련

- 주석은 한국어로 작성하세요. 다만, 사용자의 프롬프트에서 특정 언어로 작성하라고 명시된 경우에는 그 언어를 따르세요.
- 한국어로 주석을 작성하는 경우 함수나 구조체 이름 다음에 한 칸을 띄어쓰고 작성하세요.
  - 올바른 예: `// GetUser 는 사용자 정보를 반환합니다.`
  - 잘못된 예: `//GetUser는 사용자 정보를 반환합니다.`

# 반드시 참고해야 할 파일

- 원본 과제 설명은 homework.md에 존재하지만, 변경한 부분이 있으니 PRD.md도 함께 참고하세요.

# 코딩 스타일

- FCQN(완전한 패키지 경로)을 코드에 직접 작성하지 말고, 반드시 `import` 구문을 사용하여 참조하세요.

# 모범 답안

- 코드를 작성함에 있어 **반드시** `/Volumes/personal/excivis/runtric/runtric-server` 경로에 존재하는 Runtric 서버 프로젝트의 코드를 참고하여 일관된 스타일과 패턴을 유지하세요.

# 테스트 규약

- **핵심 규칙**
  - 비즈니스 로직을 작성하거나 수정하면 반드시 관련 테스트를 추가/수정하고 실행 결과를 확인합니다.
  - E2E 테스트도 존재(approval-request-service에)하니 놓치지 말고 점검합니다.
- **테스트 범위 우선순위**
  - 서비스 레이어를 기본 대상으로 한다. 컨트롤러/리포지토리는 스프링 기본 동작 이상을 추가했을 때만 테스트한다.
  - 모듈 우선순위: common-core(보안·예외), employee-service(JPA), approval-request-service(Mongo+gRPC+외부 REST), approval-processing-service(gRPC 큐·컨트롤러), notification-service(WebSocket/REST).
- **테스트 종류 및 도구**
  - 단위/슬라이스: 외부 의존 mock, in-memory 데이터 구조 검증.
  - 통합: `@SpringBootTest` + `@ActiveProfiles("test")`; employee-service는 H2, approval-request-service는 Testcontainers Mongo 필수, approval-processing-service는 인메모리, 필요 시 MySQL 컨테이너는 `@Tag("e2e")`로 분리.
  - 계약: REST는 `MockWebServer`(또는 WireMock)로 경로·헤더·바디를 검증, gRPC는 `InProcessServerBuilder`와 스텁으로 페이로드·재시도 확인.
  - 시나리오(E2E, 선택): MySQL/H2 + Mongo 컨테이너 + in-process gRPC + MockWebServer를 조합해 “결재 생성→승인/반려→알림 전송”을 검증하며 `src/test/resources/application-e2e.yml`에 별도 설정을 둔다.
- **스타일 가이드**
  - JUnit5, AssertJ 사용. `@Nested`로 시나리오를 구분하고 Given/When/Then 한글 주석을 유지한다.
  - 예외 검증 시 `// when & then: 예외 검증` 주석을 사용한다.
  - CUD 테스트는 반환 DTO 검증 + 저장소/DB 상태 검증을 모두 수행한다. 조회 테스트는 반환값 검증에 집중한다.
- **검증 범위 세부 원칙**
  - 조회(Read): 상태 변화가 없으므로 반환 DTO/리스트 내용만 검증한다. 단, 정렬·필터·권한 필터링이 개입된 경우 핵심 필드(정렬 키, 접근 제어 적용 여부)를 반드시 확인한다.
  - 생성/수정/삭제(CUD): 비즈니스 규칙이 개입되므로 “계약 이행(반환 DTO)”과 “영속 상태(DB/저장소)”를 모두 검증한다. approval-request의 `finalStatus`·`updatedAt`, employee-service의 role/department 변경 등 내부 필드 반영까지 확인한다.
- **REST/gRPC 계층 테스트**
  - REST 컨트롤러는 스프링 기본 위임만 있을 때는 생략 가능하지만, 밸리데이션/권한/경로 매핑/예외 매핑을 추가했다면 400/403/404/201 등 대표 케이스를 `@WebMvcTest` 또는 통합 테스트로 확보한다.
  - gRPC 엔드포인트는 프로토콜 계약 핵심이므로 in-process 서버로 최소 happy path, 오류/재시도, 단계 순서 제어를 검증한다(ApprovalRequest→Processing, Processing→ApprovalRequest 콜백 모두 포함).
- **인증/보안 테스트**
  - dev JWT 토큰을 test 프로필에 주입하거나 `AuthUtil`/`JwtAuthenticator`를 Mockito로 스텁한다.
  - SecurityContext 수동 설정이 필요하면 `TestingAuthenticationToken`을 사용한다.
- **실행 규칙**
  - 컨테이너 의존 테스트가 오래 걸리는 경우 `@Tag("e2e")`로 표시하여 선택 실행한다.
  - 커밋/PR 전 `./gradlew clean test` 또는 모듈 단위 `./gradlew :module:test`를 실행하고 결과를 공유한다.

# 현재 상황 업데이트

- 작업을 끝내고 현재 프로젝트 상황을 적절히 <current_status> 태그 안에 내용을 수정하여 반영하세요.

</project_guidelines>
