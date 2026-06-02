---
description: Controller, Service, Domain 계층 간 데이터 전달 및 DTO/Domain 매핑 규칙
alwaysApply: true
---

# 계층 간 통신 및 DTO/Domain 매핑 규칙

Controller, Service, Domain 계층 간의 데이터 전달 규칙을 정의한다. DDD Rich Domain 모델과 CQRS 라이트 패턴을 기반으로 한다.

## 1. 핵심 원칙

- **계층 분리:** Service 계층은 Web 계층(Controller)의 존재를 몰라야 한다. HTTP Request/Response 객체나 Web DTO가 Service 계층으로 넘어와서는 안 된다.
- **Rich Domain 보호:** Controller에서 넘어온 불완전한 데이터를 Service 계층에서 바로 Rich Domain 객체로 생성/변환하여 파라미터로 넘기지 않는다. Domain 객체는 비즈니스 로직의 주체이며, 데이터 전달용 캐리어가 아니다.

## 2. Service 계층 입력 스펙: Command / Query 객체 사용

- Service 메서드의 파라미터는 행위의 의도가 명확히 담긴 전용 객체(Command 또는 Query)를 사용한다.
- 연관된 Command/Query는 `sealed interface`를 사용하여 하나의 파일 내에 그룹화한다 (DTO 폭발 방지).
- 데이터 홀더 역할을 위해 Java는 `record`, Kotlin은 `data class`를 활용한다.

## 3. CQRS 기반 흐름 제어 (Read / Write 분리)

### 3.1. 상태 변경 (Write / Command) 흐름

- 대상: POST, PUT, PATCH, DELETE 요청
- 흐름: Controller (Web DTO) → Service (Command) → Rich Domain (비즈니스 로직 실행) → Repository (Save)
- Controller에서 Web DTO를 Service Command로 변환하여 호출한다.
- Service는 Command 데이터를 검증하고 Domain 객체를 조회/생성하여 상태를 변경한다.

### 3.2. 단순 조회 (Read / Query) 흐름

- 대상: GET 요청 (단순 데이터 렌더링 목적) 및 POST를 사용한 검색 요청 (`POST /resources/search` 등)
- 흐름: Controller → Query DTO → Repository (Projection) → Controller
- 단순 조회를 위해 Rich Domain 객체를 거치지 않는다.
- JPA Projection, Querydsl, jOOQ 등을 사용하여 DB에서 읽어올 때부터 Read DTO(`record`/`data class`)로 직접 매핑하여 반환한다.

## 4. Mapper 계층 구현 규칙

- Web DTO를 Service Command로 변환하는 로직은 다음 중 하나에 위치시킨다:
  - Controller 내부
  - Web DTO 내부(`toCommand()` 메서드)
  - 전용 Mapper 클래스(MapStruct 등)

## 5. Service 반환 타입 규칙

- **Command 흐름 (상태 변경):**
  - 생성(Create): 생성된 엔티티의 ID를 반환한다.
  - 수정/삭제(Update/Delete): `void`를 반환한다.
- **Query 흐름 (조회):**
  - Read DTO(`record`/`data class`)를 직접 반환한다. Domain 객체를 반환하지 않는다.

## 6. 금지 패턴

- Service 메서드에 Web DTO를 파라미터로 받지 않는다.
- 단순 조회 시 Rich Domain 객체를 경유하지 않는다.
- Controller에서 Domain 객체를 직접 반환하지 않는다.
