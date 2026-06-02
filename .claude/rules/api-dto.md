---
description: API-First 개발 및 OpenAPI 기반 DTO 자동 생성 규칙
alwaysApply: true
---

# API-First 개발 및 DTO 자동 생성 규칙

웹 계층의 Request/Response DTO 및 Controller 인터페이스는 OpenAPI 스펙에서 자동 생성한다. 수동 작성을 금지한다.

## 1. 핵심 원칙

- 웹 계층의 Request, Response DTO 및 Controller 인터페이스를 개발자가 직접 수동으로 작성하지 않는다.
- 프론트엔드와의 스펙 불일치를 원천 차단하기 위해, 모든 웹 계층 입출력 스펙은 단일 파일(`openapi.yaml`)에서 관리하고 코드로 자동 생성한다.

## 2. 스펙 정의

- 모든 API 스펙은 실제 소스 코드를 작성하기 전에 `openapi.yaml` 파일에 가장 먼저 정의한다.
- 소스 코드 내부에 Swagger 관련 애노테이션을 직접 붙여서 문서화하는 역방향 방식은 사용하지 않는다.

## 3. 자동 생성 및 Record 활용

- 빌드 도구(Gradle 또는 Maven)에 OpenAPI Generator 플러그인을 연동하여, 빌드 시점에 Controller 인터페이스와 DTO 모델을 자동 생성한다.
- 제너레이터 설정에서 모델 스타일을 반드시 `record`로 지정하여, 불변 객체 특성을 살리고 보일러플레이트를 최소화한다.

## 4. 개발 흐름

1. 추가하거나 변경할 API의 엔드포인트와 데이터 구조를 `openapi.yaml`에 작성한다.
2. 로컬 빌드를 실행하여 API 인터페이스와 Record 기반 DTO 클래스를 자동 생성한다.
3. 자동 생성된 인터페이스를 `implements` 하는 실제 Controller 클래스를 작성하고 비즈니스 로직을 연결한다.
4. Controller에서 Service 계층으로의 데이터 전달은 `layer-communication-rules.md`의 Command/Query 변환 규칙을 따른다.

## 5. 유지보수 및 예외

- API 스펙 변경 시 소스 코드를 직접 수정하지 않는다. `openapi.yaml`을 먼저 수정하고 재빌드하여 코드를 덮어씌운다.
- 동적 필드 등 OpenAPI 스펙으로 표현하기 어려운 특수한 입출력이 필요한 경우에만 예외적으로 DTO 수동 작성을 허용한다.
