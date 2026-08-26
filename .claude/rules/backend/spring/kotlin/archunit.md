---
description: 계층 의존 방향과 슬라이스 규율을 ArchUnit 테스트로 강제하는 규칙 (Kotlin, Layered/Hexagonal 공통)
paths:
  - "**/*ArchitectureTest.kt"
---

# ArchUnit 아키텍처 테스트 규칙 (Kotlin)

계층 의존 방향과 슬라이스 규율을 **테스트로 강제**하는 규칙이다. 서술만으로는 지켜지지 않는 아키텍처 경계를 ArchUnit으로 검증해 위반 시 빌드가 실패하게 한다. Layered/Hexagonal 아키텍처 스타일 양쪽에 공통 적용된다(스타일별로 적용할 규칙 집합만 다르다).

이 프로젝트가 Java를 채택했다면 이 파일은 적용 대상이 아니다(`java/archunit.md` 참조). ArchUnit API 자체는 언어 무관이지만, 테스트 코드는 저장소 언어로 작성하므로 언어별 파일을 둔다.

## 1. 의존성

- `com.tngtech.archunit:archunit-junit5`를 `testImplementation`으로 추가한다.
- 아키텍처 테스트는 `@AnalyzeClasses(packages = ["{base.package}"], importOptions = [ImportOption.DoNotIncludeTests::class])`로 프로덕션 클래스만 분석한다.

## 2. 계층 의존 방향 강제 (공통)

`Architectures.layeredArchitecture().consideringAllDependencies()`로 계층을 정의하고 접근 가능 계층을 제한한다.

### 2.1. Hexagonal

- 계층: `domain`(`{base}.domain..`), `application`(`{base}.application..`), `adapter`(`{base}.adapter..`).
- 규칙: `domain`은 `application`·`adapter`만 접근 가능, `application`은 `adapter`만 접근 가능, `adapter`는 어떤 계층에서도 접근 불가.

```kotlin
Architectures.layeredArchitecture()
    .consideringAllDependencies()
    .layer("domain").definedBy("{base}.domain..")
    .layer("application").definedBy("{base}.application..")
    .layer("adapter").definedBy("{base}.adapter..")
    .whereLayer("domain").mayOnlyBeAccessedByLayers("application", "adapter")
    .whereLayer("application").mayOnlyBeAccessedByLayers("adapter")
    .whereLayer("adapter").mayNotBeAccessedByAnyLayer()
    .check(classes)
```

### 2.2. Layered

- 계층: `controller`/`web`, `service`, `repository`, `domain`. 순서(바깥→안쪽): Controller → Service → Repository → Domain.
- 규칙: 각 계층은 자신보다 안쪽 계층만 접근 가능하고, 안쪽 계층은 바깥 계층을 참조할 수 없다(`layer-communication-rules.md` 6번을 ArchUnit으로 강제). 패키지 정의는 실제 구조에 맞춰 지정한다.

## 3. 슬라이스 규율 (공통)

- 애그리거트/애플리케이션 슬라이스 간 순환 의존을 금지한다.

```kotlin
SlicesRuleDefinition.slices()
    .matching("{base}.domain.(*)..")
    .should().beFreeOfCycles()
    .check(classes)
```

- `application` 슬라이스에도 동일하게 순환 금지 규칙을 적용한다.

## 4. 애그리거트 간 참조 규율 (Pragmatic flavor 전용)

- Pragmatic flavor(도메인=엔티티, 애그리거트 간 객체 참조 허용, `domain.md` 6번)에서는 다른 슬라이스에 대해 **조회 메서드만 호출**하도록 커스텀 `ArchCondition`으로 강제한다: 대상이 다른 슬라이스에 속하고 `record`(Kotlin `data class`)/`enum`이 아니면, 호출 메서드명이 `get`/`is`/`ensure`로 시작하는 것만 허용한다.
- Clean flavor에서는 애그리거트 간 참조를 ID로만 하므로(`domain.md` 6번) 이 규칙을 적용하지 않는다(2.1의 계층 규칙 + 3의 순환 금지로 충분하다).

## 5. 배치

- 아키텍처 테스트 클래스는 base package 루트의 테스트 소스(예: `{base}.HexagonalArchitectureTest` 또는 `LayeredArchitectureTest`)에 둔다.
- `@ArchTest` 함수/필드 단위로 규칙을 나눠 위반 지점을 명확히 드러낸다.

## 6. 금지 패턴

- 아키텍처 경계를 서술로만 남기고 테스트로 강제하지 않는 상태를 방치하지 않는다(이 파일이 존재하는 프로젝트는 아키텍처 테스트를 유지한다).
- 아키텍처 테스트를 비활성화(`@Disabled`)하거나 위반을 예외 목록으로 광범위하게 우회하지 않는다. 위반은 구조를 고쳐 해소한다.
