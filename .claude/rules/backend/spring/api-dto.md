---
description: API-First 개발 및 OpenAPI 기반 DTO 자동 생성 규칙
paths:
  - "**/openapi*.yaml"
  - "**/openapi*.yml"
  - "**/*Controller.java"
  - "**/*Controller.kt"
---

# API-First 개발 및 DTO 자동 생성 규칙

웹 계층의 Request/Response DTO 및 Controller 인터페이스는 OpenAPI 스펙에서 자동 생성한다. 수동 작성을 금지한다. 아키텍처 스타일(Layered/Hexagonal)과 무관하게 적용된다.

이 프로젝트가 NestJS나 FastAPI를 채택했다면 이 파일은 적용 대상이 아니다. 백엔드 스택(Spring/NestJS/FastAPI)은 한 프로젝트에서 하나만 사용하므로, 실제로 채택하지 않은 스택의 규칙 파일은 프로젝트에서 제외한다 (`nestjs.md`, `fastapi.md` 참조).

## 1. 핵심 원칙

- 웹 계층의 Request, Response DTO 및 Controller 인터페이스를 개발자가 직접 수동으로 작성하지 않는다.
- 프론트엔드와의 스펙 불일치를 원천 차단하기 위해, 모든 웹 계층 입출력 스펙은 단일 파일(`openapi.yaml`)에서 관리하고 코드로 자동 생성한다.

## 2. 스펙 정의

- 모든 API 스펙은 실제 소스 코드를 작성하기 전에 `openapi.yaml` 파일에 가장 먼저 정의한다.
- 소스 코드 내부에 Swagger 관련 애노테이션을 직접 붙여서 문서화하는 역방향 방식은 사용하지 않는다.

## 3. 자동 생성 및 불변 모델 활용

- 빌드 도구(Gradle 또는 Maven)에 OpenAPI Generator 플러그인을 연동하여, 빌드 시점에 Controller 인터페이스와 DTO 모델을 자동 생성한다.
- 모델은 반드시 불변 객체로 생성한다. 언어별 설정: Java는 `spring` 제너레이터에서 모델 스타일을 `record`로 지정하고, Kotlin은 `kotlin-spring` 제너레이터를 사용해 `data class`로 생성한다.
- 제너레이터 설정에서 `useBeanValidation`(또는 동등 옵션)을 활성화하여, 스펙의 제약조건(`shared/rest-api.md` 8번)이 Bean Validation 애노테이션으로 자동 생성되도록 한다. 애노테이션을 코드에 직접 추가하지 않는다.
- 중첩 스키마(Value Object 그룹화, `shared/architecture.md` 4번 참조)를 참조하는 필드는 제너레이터가 자동으로 `@Valid`를 붙여 계층 검증(cascading validation)이 되도록 한다.
- Controller가 구현하는 생성된 인터페이스의 `@RequestBody` 파라미터에 `@Valid`가 포함되어 있는지 확인한다. 누락된 경우에만 Controller 구현체에서 보완한다.

## 4. 개발 흐름

1. 추가하거나 변경할 API의 엔드포인트와 데이터 구조를 `openapi.yaml`에 작성한다.
2. 로컬 빌드를 실행하여 API 인터페이스와 불변 모델(`record`/`data class`) 기반 DTO 클래스를 자동 생성한다.
3. 자동 생성된 인터페이스를 `implements` 하는 실제 Controller 클래스를 작성하고 비즈니스 로직을 연결한다.
4. Controller에서 Service 계층으로의 데이터 전달은 아키텍처 스타일에 맞는 Command/Query 변환 규칙을 따른다 (Layered는 `layer-communication-rules.md`, Hexagonal은 `ports-and-adapters.md` 6.1절).

## 5. 유지보수 및 예외

- API 스펙 변경 시 소스 코드를 직접 수정하지 않는다. `openapi.yaml`을 먼저 수정하고 재빌드하여 코드를 덮어씌운다.
- 동적 필드 등 OpenAPI 스펙으로 표현하기 어려운 특수한 입출력이 필요한 경우에만 예외적으로 DTO 수동 작성을 허용한다.

## 6. 금지 패턴

- 웹 계층의 Request/Response DTO 및 Controller 인터페이스를 수동으로 작성하지 않는다.
- 소스 코드에 Swagger 관련 애노테이션을 직접 붙여 스펙을 역방향으로 문서화하지 않는다.
- Bean Validation 애노테이션을 코드에 직접 추가하지 않는다(제너레이터 설정으로 자동 생성).
- API 스펙 변경 시 소스 코드를 먼저 수정하지 않는다. `openapi.yaml`을 먼저 수정하고 재빌드한다.
