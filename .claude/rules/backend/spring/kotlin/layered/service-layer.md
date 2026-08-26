---
description: Service 계층 클래스 분리(Finder/Service) 및 트랜잭션 선언 규칙 (Kotlin)
paths:
  - "**/*Service.kt"
  - "**/*Finder.kt"
---

# Service 계층 클래스 분리 및 트랜잭션 규칙

서비스 기능 구현 시 Read 전용 Finder와 Write 처리 Service를 분리하고, 트랜잭션을 클래스 단위로 선언한다.

이 프로젝트가 Hexagonal(Ports & Adapters)을 채택했다면 이 파일은 적용 대상이 아니다. Finder/Service가 UseCase 인터페이스를 구현하고 Port를 통해서만 영속성에 접근하는지가 핵심 차이다 (`hexagonal/service-layer.md` 참조). 마찬가지로 이 프로젝트가 Java를 채택했다면 이 파일은 적용 대상이 아니다. Java와 Kotlin 규칙 파일을 한 프로젝트에서 동시에 쓰지 않으므로, 실제로 채택하지 않은 언어의 규칙 파일도 프로젝트에서 제외한다 (`java/layered/service-layer.md` 참조).

## 1. 핵심 원칙

- 하나의 도메인 영역에 대해 **Finder 클래스**와 **Service 클래스**를 분리하여 작성한다.
- **Finder**: 조회(Read) 전용 로직을 담당한다.
- **Service**: 생성, 수정, 삭제 등 상태 변경(Write) 로직을 담당한다.

## 2. 트랜잭션 선언 규칙

- 트랜잭션 어노테이션은 **클래스 단위**로 선언한다. 메서드마다 개별 선언하지 않는다.
- **Finder 클래스**: `@Transactional(readOnly = true)`를 클래스에 선언한다.
- **Service 클래스**: `@Transactional`을 클래스에 선언한다.
- Kotlin 클래스는 기본이 `final`이므로 Spring 프록시가 동작하도록 `kotlin-spring`(all-open) 컴파일러 플러그인 적용을 전제로 한다. 클래스에 `open`을 수동으로 붙이지 않는다.

## 3. 클래스 구조 예시

### 3.1. Finder (조회 전용)

- 클래스명: `{Domain}Finder` (예: `OrderFinder`, `UserFinder`)
- 역할: 조회 로직, 존재 여부 확인, 목록/상세 조회
- `layer-communication-rules.md`의 Query 흐름(3.2절)에 대응한다.

### 3.2. Service (상태 변경)

- 클래스명: `{Domain}Service` (예: `OrderService`, `UserService`)
- 역할: 생성, 수정, 삭제 등 비즈니스 로직 실행
- `layer-communication-rules.md`의 Command 흐름(3.1절)에 대응한다.
- Service 내부에서 조회가 필요한 경우, 같은 도메인의 Finder를 주입받아 사용할 수 있다.

## 4. 금지 패턴

- Finder 클래스에 상태 변경 로직을 포함하지 않는다.
- 메서드 단위로 `@Transactional` 또는 `@Transactional(readOnly = true)`를 개별 선언하지 않는다.
