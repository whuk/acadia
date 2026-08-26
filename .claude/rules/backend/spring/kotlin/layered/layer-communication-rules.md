---
description: Controller, Service, Domain 계층 간 데이터 전달 및 DTO/Domain 매핑 규칙 (Kotlin)
---

# 계층 간 통신 및 DTO/Domain 매핑 규칙

Controller, Service, Domain 계층 간의 데이터 전달 규칙을 정의한다. DDD Rich Domain 모델과 CQRS 라이트 패턴을 기반으로 한다.

이 프로젝트가 Hexagonal(Ports & Adapters)을 채택했다면 이 파일은 적용 대상이 아니다. Layered와 Hexagonal을 한 프로젝트에서 동시에 쓰지 않으므로, 실제로 채택하지 않은 아키텍처 스타일의 규칙 파일은 프로젝트에서 제외한다 (`hexagonal/ports-and-adapters.md` 참조). 마찬가지로 이 프로젝트가 Java를 채택했다면 이 파일은 적용 대상이 아니다. Java와 Kotlin 규칙 파일을 한 프로젝트에서 동시에 쓰지 않으므로, 실제로 채택하지 않은 언어의 규칙 파일도 프로젝트에서 제외한다 (`java/layered/layer-communication-rules.md` 참조).

## 1. 핵심 원칙

- **계층 분리:** Service 계층은 Web 계층(Controller)의 존재를 몰라야 한다. HTTP Request/Response 객체나 Web DTO가 Service 계층으로 넘어와서는 안 된다.
- **Rich Domain 보호:** Controller에서 넘어온 불완전한 데이터를 Service 계층에서 바로 Rich Domain 객체로 생성/변환하여 파라미터로 넘기지 않는다. Domain 객체는 비즈니스 로직의 주체이며, 데이터 전달용 캐리어가 아니다.

## 2. Service 계층 입력 스펙: Command / Query 객체 사용

- Service 메서드의 파라미터는 행위의 의도가 명확히 담긴 전용 객체(Command 또는 Query)를 사용한다.
- 연관된 Command/Query는 `sealed interface`를 사용하여 하나의 파일 내에 그룹화한다 (DTO 폭발 방지).
- 데이터 홀더 역할을 위해 `data class`를 활용한다.

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
- JPA Projection, Querydsl, jOOQ 등을 사용하여 DB에서 읽어올 때부터 Read DTO(`data class`)로 직접 매핑하여 반환한다.

## 4. Mapper 계층 구현 규칙

- Web DTO를 Service Command로 변환하는 로직은 다음 중 하나에 위치시킨다:
  - Controller 내부
  - Web DTO 내부(`toCommand()` 메서드)
  - 전용 Mapper 클래스(MapStruct 등)

## 5. Service 반환 타입 규칙

- **Command 흐름 (상태 변경):**
  - 생성(Create): 생성된 엔티티의 ID를 반환한다.
  - 수정/삭제(Update/Delete): `Unit`을 반환한다(반환 타입 생략).
- **Query 흐름 (조회):**
  - Read DTO(`data class`)를 직접 반환한다. Domain 객체를 반환하지 않는다.

## 6. 전체 계층 의존 방향 (Hexagonal 스타일)

Controller/Service뿐 아니라 Repository, Domain(JPA 영속성 매핑을 겸함, `domain.md` 9번 참조)을 포함한 전체 계층의 의존 방향을 정의한다.

- 계층 순서(바깥 → 안쪽): **Controller → Service(Finder/Service) → Repository → Domain**
- 의존은 항상 바깥 계층에서 안쪽 계층으로만 향한다. 바로 아래 계층뿐 아니라 몇 단계 더 안쪽에 있는 계층을 건너뛰어 참조하는 것도 허용한다 (예: Controller가 Domain의 Value Object를 식별자 타입으로 직접 참조하는 경우 등).
- 안쪽 계층이 자신보다 바깥쪽 계층을 참조하는 것은 몇 단계이든, 어떤 경우에도 금지한다.
- Domain은 계층 구조의 최중심(core)이며 어떤 바깥 계층에도 의존하지 않는다. `domain.md`의 "인프라 비종속" 원칙과 일치한다.
- Domain 클래스가 JPA 엔티티 역할을 겸하므로(`domain.md` 9번), 별도의 Entity 계층이나 Entity ↔ Domain 간 변환 책임은 존재하지 않는다.
- `service-layer.md`, `repository.md` 등 개별 rule 파일에 정의된 더 좁은 범위의 캡슐화 규칙(예: SqlBuilder는 JdbcRepository를 통해서만 접근하고 Finder/Service에서 직접 사용하지 않는다)은 이 규칙보다 우선한다.

### 6.1. 금지 패턴 (역참조)

- Domain 패키지가 Repository, Service, Controller 패키지의 클래스를 import 하지 않는다.
- Repository 패키지가 Service, Controller 패키지의 클래스를 import 하지 않는다.
- Service(Finder/Service) 패키지가 Controller 패키지의 클래스(Web DTO 등)를 import 하지 않는다 (1번 원칙과 동일).

## 7. 파라미터 그룹화 (Value Object) 규칙

API 스펙부터 Domain까지 전 계층에 걸쳐 적용되는 공통 규칙이다. 각 계층은 자신의 표현 방식으로 이 규칙을 구현하되, 그룹화 기준은 동일하게 공유한다.

### 7.1. 트리거 기준

- 하나의 엔드포인트, 메서드, 생성자가 받는 **의미적으로 연관된 파라미터가 4개 이상**이면, 원시 값(String, Int 등)을 개별 나열하지 않고 하나의 Value Object로 묶는다.
- 서로 연관 없는 파라미터가 우연히 4개 이상인 경우는 그룹화 대상이 아니다. 판단 기준은 항상 "함께 하나의 개념을 이루는가"이다 (예: `street`, `city`, `zipCode`, `country` → `Address`).
- 연관된 값이 3개 이하라도 여러 곳에서 재사용되는 개념이면 Value Object로 추출하는 것을 권장한다 (강제 아님).

### 7.2. 계층별 적용 방식

| 계층 | 위치 | 적용 방법 |
|---|---|---|
| OpenAPI 스펙 | `openapi.yaml` | 연관 필드를 `components/schemas`에 별도 스키마로 정의하고 `$ref`로 참조한다 (`shared/rest-api.md` 8번과 동일). 인라인으로 4개 이상 필드를 나열하지 않는다 |
| Web DTO | 자동 생성 Request/Response `data class` | 스펙의 `$ref` 구조가 그대로 중첩 `data class`로 생성된다 (`api-dto.md` 3번) |
| Command/Query | Service 입력 객체 | Web DTO의 중첩 필드를 그대로 옮기지 않고, Command/Query 전용 Value Object로 재정의해서 포함한다 (`toCommand()`/Mapper에서 변환) |
| Domain | 생성자 / 상태 변경 메서드 | Value Object를 파라미터로 받는다 (`domain.md` 3번·4번과 일치) |
| Repository | Specification / 검색 조건 객체 | `{Domain}SearchQuery` 등의 필드로 포함하고, Specification은 Value Object 단위로 조건을 조립한다 |

### 7.3. 계층 간 재사용 금지

- 각 계층은 이름이 같아도 **자신만의 타입으로 별도 정의**한다. Web 계층의 `AddressRequest`(생성 코드)와 Domain의 `Address`(Rich VO)는 다른 클래스다.
- Web DTO의 Value Object를 Service/Domain 계층에 그대로 전달하지 않는다. 계층 경계를 넘을 때는 반드시 변환한다 (1번, 6번 원칙과 동일).

### 7.4. 예시

```yaml
# openapi.yaml
components:
  schemas:
    Address:
      type: object
      required: [street, city, zipCode, country]
      properties:
        street: { type: string, minLength: 1, maxLength: 100 }
        city: { type: string, minLength: 1, maxLength: 50 }
        zipCode: { type: string, pattern: '^[0-9]{5}$' }
        country: { type: string, minLength: 2, maxLength: 2 }
    CreateUserRequest:
      type: object
      properties:
        name: { type: string }
        email: { type: string, format: email }
        address:
          $ref: '#/components/schemas/Address'
```

```kotlin
// Web DTO — 자동 생성 (개발자가 직접 작성하지 않음)
data class Address(
    @field:NotBlank @field:Size(max = 100) val street: String,
    @field:NotBlank @field:Size(max = 50) val city: String,
    @field:NotBlank @field:Pattern(regexp = "^[0-9]{5}$") val zipCode: String,
    @field:NotBlank @field:Size(min = 2, max = 2) val country: String
)

data class CreateUserRequest(
    @field:NotBlank val name: String,
    @field:Email val email: String,
    @field:Valid @field:NotNull val address: Address   // 중첩 객체 → @Valid 자동 추가
)
```

```kotlin
// Command — Web DTO와 동일한 제약조건 유지 (다중 진입점 방어, 7.5 참조)
data class Address(
    @field:NotBlank @field:Size(max = 100) val street: String,
    @field:NotBlank @field:Size(max = 50) val city: String,
    @field:NotBlank @field:Pattern(regexp = "^[0-9]{5}$") val zipCode: String,
    @field:NotBlank @field:Size(min = 2, max = 2) val country: String
)

sealed interface UserCommand {
    data class Create(
        @field:NotBlank val name: String,
        @field:Email val email: String,
        @field:Valid @field:NotNull val address: Address
    ) : UserCommand
}
```

```kotlin
// Domain (domain.md 3번, 9번) — orm.xml에서 <embeddable>로 매핑
class User private constructor(
    private val name: String,
    private val email: Email,
    private val address: Address
) {
    companion object {
        fun create(name: String, email: Email, address: Address): User {
            return User(name, email, address)
        }
    }
}
```

### 7.5. 검증 책임 분리 (다중 진입점 방어)

- Command/Query를 포함한 모든 계층의 `data class`에 Bean Validation 애노테이션을 붙인다. Service가 Controller 외의 경로(배치, 메시지 컨슈머, 다른 서비스의 내부 호출 등)로 호출될 수 있어, Web DTO 검증만으로는 방어되지 않는 진입점이 있기 때문이다.
- 생성자 프로퍼티에 붙이는 Bean Validation 애노테이션에는 `@field:` use-site target을 명시한다. 타겟 없이 붙이면 생성자 파라미터에만 적용되어 필드 기반 검증이 동작하지 않을 수 있다.
- Service 클래스에는 `@Validated`를, Command/Query 파라미터에는 `@Valid`를 선언하여 메서드 호출 시점에 자동으로 검증되도록 한다.
- Web DTO와 Command/Query의 제약조건 값(길이, 패턴, 범위 등)은 동일하게 유지한다. Web DTO는 `openapi.yaml`에서 자동 생성되므로, Command/Query 쪽 제약조건은 스펙이 바뀔 때 수동으로 동기화한다.
- 이 중복은 의도된 것이다: Controller를 거치지 않는 진입 경로를 방어하기 위함이며 제거 대상이 아니다.
- Bean Validation은 형식/구문 검증(필수값, 길이, 패턴 등)까지만 담당한다. Web 계층 실패는 400 Bad Request, Service 계층에서 발생한 `ConstraintViolationException`은 GlobalExceptionHandler에서 400으로 매핑한다 (`shared/rest-api.md` 3번).
- 비즈니스 규칙 검증(잔액 부족, 중복 가입 등)은 여전히 Domain 객체의 자기 검증(`domain.md` 5번)이 전담한다. 실패 시 422 Unprocessable Entity.

## 8. 금지 패턴

- Service 메서드에 Web DTO를 파라미터로 받지 않는다.
- 단순 조회 시 Rich Domain 객체를 경유하지 않는다.
- Controller에서 Domain 객체를 직접 반환하지 않는다.
- Kotlin `data class`의 검증 애노테이션에 `@field:` use-site target을 누락하지 않는다.
