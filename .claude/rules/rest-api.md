---
description: REST API 설계 원칙 및 OpenAPI 스펙 작성 규칙
alwaysApply: true
---

# REST API 설계 및 OpenAPI 스펙 작성 규칙

REST API의 URI, HTTP 메서드, 상태 코드, 요청/응답 본문, 버전 관리 등 설계 규칙과 OpenAPI 스펙 작성 규칙을 정의한다. `api-dto.md`의 "openapi.yaml 먼저 작성 → 코드 생성" 흐름을 전제로 한다.

## 1. URI 설계

- 리소스 이름은 **복수 명사**를 사용한다 (예: `/orders`, `/users`).
- 동사를 URI에 포함하지 않는다. 행위는 HTTP 메서드로 표현한다. 단, 복잡한 검색을 위한 POST 엔드포인트는 예외적으로 `/search` 서브리소스를 허용한다 (2번 HTTP 메서드 참조).
- 복합 단어는 **kebab-case**를 사용한다 (예: `/order-items`).
- 리소스 중첩은 **2단계 이하**로 제한한다 (예: `/users/{userId}/orders`까지 허용, 그 이상은 독립 리소스로 분리).
- **trailing slash를 금지**한다 (`/users/`가 아닌 `/users`).
- 파일 확장자를 URI에 포함하지 않는다. Content-Type 헤더로 협상한다.

## 2. HTTP 메서드

- **GET**: 리소스 조회. 멱등하다.
- **POST**: 리소스 생성.
- **PUT**: 리소스 전체 교체. 멱등하다.
- **PATCH**: 리소스 부분 수정. 변경할 필드만 포함한다.
- **DELETE**: 리소스 삭제. 멱등하다.
- 부분 수정에는 PUT이 아닌 **PATCH**를 사용한다.
- 검색 조건이 복잡하여 GET query string으로 표현하기 어려운 경우, **POST**를 사용한 검색을 허용한다 (예: `POST /orders/search`).

## 3. HTTP 상태 코드

- **200 OK**: 조회, 수정, 삭제 성공 시 기본 응답.
- **201 Created**: 리소스 생성 성공. 반드시 `Location` 헤더에 생성된 리소스의 URI를 포함한다. Controller가 Service로부터 반환받은 ID를 사용하여 `Location` 헤더 URI를 조립한다.
- **204 No Content**: 성공했으나 응답 본문이 없는 경우 (DELETE, 일부 PUT/PATCH).
- **400 Bad Request**: 요청 구문 오류, 필수 필드 누락 등 클라이언트의 형식적 오류.
- **401 Unauthorized**: 인증 실패.
- **403 Forbidden**: 인가 실패 (인증은 되었으나 권한 없음).
- **404 Not Found**: 리소스가 존재하지 않음.
- **409 Conflict**: 리소스 상태 충돌 (중복 생성, 낙관적 잠금 실패 등).
- **422 Unprocessable Entity**: 형식은 올바르나 비즈니스 규칙 위반 (예: 잔액 부족).
- 400과 422를 명확히 구분한다. 구문 오류는 400, 비즈니스 검증 실패는 422를 사용한다.

## 4. 요청/응답 본문

- 단일 리소스와 컬렉션 모두 **envelope 없이 직접 반환**한다. 불필요한 래퍼 객체(`{ "data": ... }`, `{ "result": ... }`)를 사용하지 않는다. 단, 페이지네이션 응답은 메타데이터를 포함해야 하므로 5.1절 또는 5.2절의 구조를 따른다.
- 에러 응답은 **RFC 9457 Problem Details** 형식을 따른다:
  ```json
  {
    "type": "about:blank",
    "title": "Unprocessable Entity",
    "status": 422,
    "detail": "잔액이 부족합니다.",
    "instance": "/orders/12345"
  }
  ```
- Bean Validation 등 필드별 검증 오류 시, `errors` 배열을 추가하여 개별 필드 오류를 반환한다:
  ```json
  {
    "type": "about:blank",
    "title": "Bad Request",
    "status": 400,
    "detail": "요청 데이터가 유효하지 않습니다.",
    "instance": "/users",
    "errors": [
      { "field": "email", "message": "올바른 이메일 형식이 아닙니다." },
      { "field": "name", "message": "필수 값입니다." }
    ]
  }
  ```
- 필드명은 **camelCase**를 사용한다.
- 날짜/시간은 **ISO 8601** 형식을 사용한다 (예: `2026-03-18T09:30:00Z`).
- null 필드는 응답에서 생략한다.

## 5. 페이지네이션, 필터링, 정렬

### 5.1. 오프셋 기반 페이지네이션

- query parameter: `page` (0-based), `size`. 별도 정의가 없는 한 기본값은 `size=20`, 최대값은 `100`으로 한다.
- 응답 구조:
  ```json
  {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8
  }
  ```

### 5.2. 커서 기반 페이지네이션

- 대량 데이터나 실시간 피드에는 커서 기반을 사용한다.
- query parameter: `cursor` (opaque string), `size`.
- 응답에 `nextCursor`를 포함한다. 다음 페이지가 없으면 `nextCursor`를 생략한다.

### 5.3. 정렬

- query parameter: `sort` 필드명 사용. 내림차순은 `-` 접두사를 붙인다 (예: `sort=-createdAt`).
- 복수 정렬: 쉼표로 구분한다 (예: `sort=-createdAt,name`).

### 5.4. 필터링

- 필터 조건은 query parameter로 전달한다 (예: `status=ACTIVE`).
- 범위 필터는 `min{Field}`/`max{Field}` 패턴을 사용한다 (예: `minPrice=1000`, `maxPrice=5000`, `minCreatedAt=2026-01-01T00:00:00Z`).

## 6. 버전 관리

- **URI path 버전**을 사용한다 (예: `/v1/users`, `/v2/users`).
- 신규 API는 `/v1`부터 시작한다.
- 기존 버전을 폐기할 때 `Sunset` 헤더와 `Deprecation` 헤더를 응답에 포함한다.
- 하위 호환성을 유지하는 변경(필드 추가 등)은 버전을 올리지 않는다. 기존 필드의 삭제, 타입 변경, 필수 여부 변경 등 하위 호환성을 깨는 변경 시에만 새 버전을 도입한다.

## 7. 멱등성

- 결제, 주문 생성 등 중복 실행이 치명적인 **POST**, **PATCH** API에 한하여 클라이언트가 `Idempotency-Key` 헤더를 전송한다. 단순 조회성 데이터 생성이나 로그 기록 등 중복이 허용되는 API에는 적용하지 않는다.
- `Idempotency-Key` 값은 **UUID v4** 형식을 사용한다.
- 서버는 동일한 `Idempotency-Key`로 들어온 요청에 대해 최초 응답을 캐시하여 동일한 결과를 반환한다.
- 별도 정의가 없는 한 키의 TTL은 **24시간**으로 설정한다. TTL 만료 후 동일 키로 요청 시 새로운 요청으로 처리한다.
- GET, PUT, DELETE는 프로토콜 수준에서 멱등하므로 별도의 멱등성 키를 요구하지 않는다.

## 8. OpenAPI 스펙 작성

- 스키마(모델) 이름은 **PascalCase**를 사용한다 (예: `OrderResponse`, `CreateUserRequest`).
- 재사용 가능한 스키마는 반드시 `components/schemas`에 전역 정의하고 `$ref`로 참조한다. 인라인 스키마 정의를 최소화한다.
- 공통 필드를 가진 스키마는 `allOf`를 사용하여 합성한다.
- `operationId`를 모든 엔드포인트에 명시한다. 코드 생성 시 메서드명으로 사용된다.
- 요청/응답 예시(`example` 또는 `examples`)를 포함하여 스펙만으로 API 동작을 이해할 수 있도록 한다.
- 별도 정의가 없는 한 스펙 파일이 500줄을 초과하면 도메인별로 분할하고 `$ref`로 연결한다.
- 이 규칙에서 정의한 페이지네이션 응답 구조, Problem Details 에러 응답 등 공통 패턴은 `components/schemas`에 한 번 정의하고 재사용한다.
