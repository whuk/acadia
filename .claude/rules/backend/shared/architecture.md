---
description: 백엔드 스택 공통 아키텍처 원칙 (언어/프레임워크 무관). Spring/NestJS/FastAPI 모두 이 원칙을 전제로 각자 스택에 맞게 구현한다.
---

# 백엔드 공통 아키텍처 원칙

Java·Kotlin/Spring, NestJS, FastAPI 등 어떤 백엔드 스택을 쓰든 공통으로 적용되는 원칙이다. 각 스택별 구체적 구현 방법은 `spring/`, `nestjs/`, `fastapi/` 하위 규칙을 따른다. 이 문서는 "무엇을" 지키는지를 정의하고, "어떻게" 구현하는지는 스택별 문서가 정의한다.

## 1. CQRS-lite 흐름 (Write / Read 분리)

- **Write 흐름**: Controller/Handler(입력 검증) → Service(Command 처리) → Domain(비즈니스 로직 실행) → Repository(저장). 입력 객체를 Service로 그대로 넘기지 않고, Service 전용 Command 객체로 변환해서 넘긴다.
- **Read 흐름**: Controller/Handler → Query 객체 → Repository(Projection) → 응답. 단순 조회는 Rich Domain 객체를 거치지 않고, DB에서 읽을 때부터 Read 전용 객체로 직접 매핑해서 반환한다.
- Service 계층은 상위(Web/Controller) 계층의 존재를 몰라야 한다. 상위 계층의 입력 객체가 Service 계층으로 그대로 넘어와서는 안 된다.

## 2. Command/Query 객체

- Service 진입점의 파라미터는 행위의 의도가 명확히 담긴 전용 객체(Command 또는 Query)를 사용한다. 원시 타입을 나열하지 않는다.
- 연관된 Command/Query는 하나의 파일/모듈에 그룹화한다(파일 폭발 방지).
- Command 흐름의 반환값: 생성은 식별자, 수정/삭제는 없음(void/None). Query 흐름의 반환값: Read 전용 객체를 직접 반환하고 Domain 객체를 반환하지 않는다.

## 3. 계층 의존 방향 (전 계층 공통)

- 계층 순서(바깥 → 안쪽): **Controller/Handler → Service → Repository → Domain**. Domain이 계층 구조의 최중심(core)이며 어떤 바깥 계층에도 의존하지 않는다.
- 의존은 항상 바깥 계층에서 안쪽 계층으로만 향한다. 바로 아래 계층뿐 아니라 몇 단계 더 안쪽 계층을 건너뛰어 참조하는 것도 허용한다.
- 안쪽 계층이 자신보다 바깥쪽 계층을 참조하는 것은 몇 단계든, 어떤 경우에도 금지한다.

## 4. 파라미터 그룹화 (Value Object)

- 하나의 엔드포인트/함수/생성자가 받는 의미적으로 연관된 파라미터가 4개 이상이면, 원시 값을 개별 나열하지 않고 하나의 Value Object로 묶는다.
- 판단 기준은 "함께 하나의 개념을 이루는가"이다. 서로 무관한 파라미터가 우연히 4개 이상인 경우는 그룹화 대상이 아니다.
- API 스펙(있는 경우) → 입력 객체 → Command/Query → Domain까지, 각 계층은 이름이 같아도 자신만의 타입으로 별도 정의한다. 상위 계층의 객체를 하위 계층에 그대로 전달하지 않는다.

## 5. 다중 진입점 방어 (검증 책임)

- 입력 검증(형식/구문)은 시스템에 진입하는 모든 경로에서 이뤄져야 한다. Web 계층 검증만으로는 배치/메시지 컨슈머/다른 서비스의 직접 호출 같은 경로를 방어할 수 없다.
- Command/Query 객체 자체도 자기 검증 가능한 구조로 만든다(스택별 구현 방법은 각 스택 문서 참조). Web 계층과 Service 계층의 검증 규칙(길이, 패턴, 필수 여부)은 동일하게 유지한다.
- 비즈니스 규칙 검증(잔액 부족, 중복 가입 등)은 Domain 객체의 자기 검증이 전담한다. 형식 검증과 비즈니스 검증을 같은 계층에서 뒤섞지 않는다.

## 6. DDD Rich Domain 원칙

- 비즈니스 로직을 도메인 객체 내부에 배치한다. getter/setter만 있는 Anemic 모델을 금지한다.
- **Entity**: 고유 식별자를 가지며 생명주기 동안 상태가 변할 수 있다. 동일성은 식별자로 판단한다.
- **Value Object**: 식별자 없이 속성 값의 조합으로 동일성을 판단하는 불변 객체.
- 가능한 한 Value Object를 우선 사용하고, 식별자가 반드시 필요한 경우에만 Entity로 설계한다.
- 상태는 생성자/팩토리에서 초기화하고, 상태 변경은 의미 있는 이름의 명시적 메서드로만 수행한다(setter 미노출).
- 객체 생성 시점에 불변 조건을 검증한다. 유효하지 않은 상태의 객체는 존재할 수 없어야 한다. 검증 실패 시 명확한 도메인 예외를 던진다.
- Aggregate Root를 식별하고, 외부에서는 Aggregate Root를 통해서만 내부 객체에 접근한다. Aggregate 경계를 넘는 참조는 ID로만 한다.

## 7. Finder/Service 개념적 분리 (Read/Write)

- 조회(Read) 전용 로직과 상태 변경(Write) 로직을 별도 컴포넌트로 분리한다. 하나의 컴포넌트가 조회와 변경을 동시에 책임지지 않는다.
- 조회 전용 컴포넌트에 상태 변경 로직을 포함하지 않는다.

## 8. Repository 도구 선택: Escalation Ladder

- 항상 최하위 충분한 단계에서 시작한다. 상위 단계로 올라가려면 N+1, 복잡한 조인, 측정된 성능 병목 등 구체적 근거가 필요하다.
- 단순 조회(정적 조건 1~2개) → 동적 조건 조합 → 복잡한 조인/타입 안전 프로젝션 → 대량 벌크/집계/네이티브 SQL 순으로 에스컬레이션한다. 구체적 도구명은 스택별 문서를 따른다.
- 선제적으로 상위 단계 도구를 사용하지 않는다.

## 9. REST API 설계 원칙 (HTTP 계층을 노출하는 경우)

- 상세 규약(상태 코드 전체, Problem Details 스키마, 페이지네이션 응답 구조, 정렬/필터 문법, 멱등성 키, OpenAPI 작성 규칙)은 `shared/rest-api.md`에 정의한다. 아래는 요약이며, 표현이 어긋나면 `shared/rest-api.md`가 우선한다.
- URI: 복수 명사, kebab-case, 동사 금지(복잡한 검색은 `POST /{resource}/search` 예외), trailing slash 금지, 중첩 2단계 이하.
- HTTP 메서드: GET(조회)/POST(생성)/PUT(전체 교체)/PATCH(부분 수정)/DELETE(삭제). 부분 수정에 PUT을 쓰지 않는다.
- 상태 코드: 201(생성, `Location` 헤더 포함)/204(본문 없음)/400(구문 오류)/422(비즈니스 규칙 위반). 400과 422를 명확히 구분한다.
- 에러 응답은 RFC 9457 Problem Details 형식(`type`/`title`/`status`/`detail`/`instance`)을 따른다.
- 페이지네이션(오프셋/커서), 정렬(`-` 접두사), 필터(`min/max{Field}`), 버전 관리(URI path)는 스택 무관하게 동일한 규약을 따른다.

## 10. 금지 패턴

- getter/setter만 있는 Anemic Domain 모델을 사용하지 않는다. Domain 객체에 setter를 노출하지 않는다.
- 안쪽 계층(Domain, Repository)이 자신보다 바깥쪽 계층(Service, Controller/Handler)을 참조하지 않는다.
- Service 진입점에서 원시 타입을 개별 나열하지 않는다. Command/Query 객체로 그룹화한다.
- Query(조회) 흐름에서 Domain 객체를 직접 반환하지 않는다. Read 전용 객체를 반환한다.
- 조회 전용 컴포넌트에 상태 변경 로직을 포함하지 않는다.
- 측정된 성능 병목 등 구체적 근거 없이 선제적으로 상위 단계 Repository 도구를 사용하지 않는다.
