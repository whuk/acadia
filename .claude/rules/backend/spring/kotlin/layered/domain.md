---
description: 도메인 클래스 작성 시 적용되는 DDD Rich Domain Model 규칙 (Kotlin)
paths:
  - "**/domain/**"
---

# DDD Rich Domain 클래스 작성 규칙

도메인 패키지 내 클래스를 작성하거나 수정할 때 다음 원칙을 따른다.

이 파일은 Domain 클래스가 JPA 엔티티를 겸하는(`@Entity` 마커 + `orm.xml`) JPA(ORM) 프로젝트에 적용되는 버전이다. 이 프로젝트가 SQL-first(ORM 미사용)를 채택했다면 이 파일은 적용 대상이 아니다. Domain을 영속성과 완전히 분리하는 순수 Domain 버전(`domain-pure.md`)이 대신 적용되며, `/rw:init`이 스택에 맞는 한 버전만 남긴다.

이 프로젝트가 Hexagonal(Ports & Adapters)을 채택했다면 이 파일은 적용 대상이 아니다. Domain 클래스가 JPA 엔티티를 겸하는지(Layered) 완전히 분리되는지(Hexagonal)가 핵심 차이이므로 두 방식을 한 프로젝트에서 동시에 쓰지 않는다 (`hexagonal/domain.md` 참조). 마찬가지로 이 프로젝트가 Java를 채택했다면 이 파일은 적용 대상이 아니다. Java와 Kotlin 규칙 파일을 한 프로젝트에서 동시에 쓰지 않으므로, 실제로 채택하지 않은 언어의 규칙 파일도 프로젝트에서 제외한다 (`java/layered/domain.md` 참조).

## 1. Rich Domain Model

- 비즈니스 로직을 도메인 객체 내부에 배치한다. Anemic Domain Model(getter/setter만 있는 빈약한 모델)을 금지한다.
- 도메인 객체가 스스로 자신의 상태를 관리하고, 비즈니스 규칙을 실행한다.

## 2. 인프라 비종속 (애노테이션 최소화)

- Domain 클래스가 JPA 엔티티 역할을 겸한다. Domain과 별도로 JPA Entity 클래스를 만들지 않는다.
- 클래스에는 `@Entity` 마커 애노테이션만 남긴다. JPA 엔티티 스캔과 QueryDSL Q-class 생성(kapt 기반 애노테이션 프로세싱)이 `@Entity` 존재 여부를 기준으로 동작하기 때문에 예외적으로 허용한다.
- `@Column`, `@Id`, `@Table`, `@OneToMany` 등 구체적인 매핑 애노테이션은 소스 코드에 작성하지 않는다. 해당 정보는 JPA XML 디스크립터(`orm.xml`)로 분리한다 (9번 참조).
- Spring 어노테이션, DB 관련 의존성은 포함하지 않는다.

## 3. Entity vs Value Object 구분

- **Entity**: 고유 식별자(ID)를 가지며, 생명주기 동안 상태가 변할 수 있는 객체. 동일성은 ID로 판단한다.
- **Value Object**: 식별자 없이 속성 값의 조합으로 동일성을 판단하는 불변 객체. `data class`로 선언하면 속성 기반 `equals`/`hashCode`가 자동 제공된다.
- 가능한 한 Value Object를 우선 사용하고, 식별자가 반드시 필요한 경우에만 Entity로 설계한다.

## 4. 불변성 우선

- 상태는 생성자 또는 `companion object` 팩토리 함수를 통해 초기화한다.
- 상태 변경이 필요한 경우, 의미 있는 이름의 명시적 메서드를 통해서만 수행한다 (예: `approve()`, `cancel()`, `changePrice(newPrice)`).
- 프로퍼티는 가능한 한 `val`로 선언하고, 변경이 필요한 프로퍼티는 `private set`으로 외부 노출을 차단한다. setter를 노출하지 않는다.

## 5. 자기 검증 (Self-validating)

- 객체 생성 시점에 불변 조건(invariant)을 검증한다 (`init` 블록 또는 팩토리 함수). 유효하지 않은 상태의 객체는 존재할 수 없어야 한다.
- 상태 변경 메서드에서도 사전 조건(precondition)과 사후 조건(postcondition)을 검증한다.
- 검증 실패 시 명확한 도메인 예외를 던진다.

## 6. Aggregate Root

- 관련된 Entity와 Value Object의 클러스터에서 Aggregate Root를 식별한다.
- 외부에서는 Aggregate Root를 통해서만 내부 객체에 접근한다.
- Aggregate 경계를 넘는 참조는 ID로만 수행한다 (객체 직접 참조 금지).

## 7. 도메인 이벤트

- 중요한 상태 변화가 발생할 때 도메인 이벤트를 정의한다 (필요한 경우에만).
- 이벤트는 불변 Value Object(`data class`)로 작성하며, 발생 시점의 정보를 포함한다.

## 8. 내부 Enum 및 중첩 타입 배치

- 특정 도메인 클래스 내부에서만 사용하는 enum은 별도 파일로 분리하지 않고, 해당 도메인 클래스의 **중첩 타입(nested type)**으로 선언한다 (예: `Order.Status`, `Payment.Method`).
- 여러 도메인 클래스에서 공통으로 사용하는 enum은 도메인 패키지 내 별도 파일로 분리한다.

## 9. JPA 영속성 매핑 (orm.xml)

- Domain 클래스와 JPA Entity를 분리하지 않는다. 하나의 클래스가 Rich Domain 모델이자 영속성 대상 역할을 겸한다.
- `@Entity` 마커를 제외한 매핑 정보(`@Table`, `@Id`, `@Column`, `@OneToMany` 등에 해당하는 내용)는 클래스에 애노테이션으로 작성하지 않고, JPA XML 디스크립터(`orm.xml`)에 작성한다.
- 매핑 파일은 애그리거트 단위로 분리한다 (예: `src/main/resources/META-INF/jpa/{aggregate}-orm.xml`). 하나의 파일에 모든 애그리거트를 몰아넣지 않는다.
- access type은 `FIELD`로 지정하여 getter 유무와 무관하게 필드에 직접 매핑되도록 한다.
- JPA가 영속성 컨텍스트에서 객체를 재구성(하이드레이션)할 때 사용할 기본 생성자는 `kotlin-jpa`(no-arg) 컴파일러 플러그인으로 생성한다. 소스에 기본 생성자를 직접 작성하지 않으며, 이 생성자는 프레임워크 전용이므로 애플리케이션 코드에서 직접 호출하지 않는다.
- 5번 원칙(자기 검증)의 불변 조건 검증은 애플리케이션 코드를 통한 최초 생성/상태 변경 시점에만 적용한다. JPA 하이드레이션 경로(no-arg 생성자 + 필드 주입)는 이미 검증된 상태를 DB에서 재구성하는 것이므로 검증 대상에서 제외한다.
- Value Object도 동일한 방식으로 `orm.xml`의 `<embeddable>`로 매핑한다. 별도의 영속성 DTO나 변환 계층을 두지 않는다.
- 6번 원칙(Aggregate Root)에 따라, `orm.xml`에서도 다른 Aggregate Root에 대한 `@ManyToOne`/`@OneToMany`에 해당하는 연관관계 매핑을 정의하지 않는다. Aggregate 경계를 넘는 참조는 ID 필드로만 매핑한다.

## 10. 작성 원칙

- **언어**: 한글로 설명을 작성한다. 코드의 클래스명, 메서드명 등은 영문을 사용한다.
- **톤**: 기술 문서 톤을 유지한다. 이모지를 사용하지 않는다.
- **기존 패턴 준수**: 코드베이스에 이미 존재하는 패키지 구조, 네이밍 컨벤션, 코딩 스타일을 따른다.
- **최소 충분**: 현재 작업에 필요한 도메인 클래스만 작성한다. 미래에 필요할 수도 있는 클래스를 미리 만들지 않는다.
- **테스트 용이성**: 외부 의존성 없이 단위 테스트가 가능한 구조로 작성한다.

## 11. 금지 패턴

- Anemic Domain Model(getter/setter만 있는 빈약한 모델)을 금지한다.
- 도메인 클래스에 `@Column`, `@Id`, `@Table`, `@OneToMany` 등 구체적인 JPA 매핑 애노테이션을 직접 작성하지 않는다(`orm.xml`로 분리, 9번 참조).
- 상태 변경을 위한 setter를 노출하지 않는다 (`var` 프로퍼티의 public set 금지).
- Aggregate 경계를 넘는 참조를 객체로 직접 하지 않는다. ID로만 참조한다.
