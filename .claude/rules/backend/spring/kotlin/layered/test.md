---
description: 테스트 유형별 base class 선택 및 결정적 테스트 작성 규칙 (Kotlin)
paths:
  - "**/src/test/**"
---

# 테스트 작성 규칙

테스트 작성 시 테스트 유형별 적절한 base class를 선택하고, 필요한 인프라만 로드한다.

이 프로젝트가 Hexagonal(Ports & Adapters)을 채택했다면 이 파일은 적용 대상이 아니다. Domain/Application/Adapter 계층별로 테스트 전략이 세분화되므로 `hexagonal/test.md`를 참조한다. 마찬가지로 이 프로젝트가 Java를 채택했다면 이 파일은 적용 대상이 아니다. Java와 Kotlin 규칙 파일을 한 프로젝트에서 동시에 쓰지 않으므로, 실제로 채택하지 않은 언어의 규칙 파일도 프로젝트에서 제외한다 (`java/layered/test.md` 참조).

## 1. 핵심 원칙

- 테스트에 필요한 **최소한의 ApplicationContext**만 로드한다. 모든 테스트가 전체 컨텍스트를 올릴 필요는 없다.
- 테스트 설정(`@SpringBootTest`, `@ActiveProfiles` 등)을 개별 테스트 클래스에 직접 선언하지 않는다. `support` 패키지의 **base class를 상속**하여 사용한다.
- `Thread.sleep` 등 시간 기반 동기화를 사용하지 않는다. Fixture를 통해 **결정적(deterministic) 테스트 데이터**를 생성한다.

## 2. 테스트 유형별 base class 선택

### 2.1. 순수 단위 테스트 (base class 불필요)

- `@ExtendWith(MockitoExtension::class)` 사용. Spring 컨텍스트를 로드하지 않는다.
- Service, Validator, 도메인 로직 등 외부 의존성 없이 검증 가능한 테스트에 사용한다.
- jOOQ SqlBuilder 테스트가 이 유형에 해당한다. `DSL.using(SQLDialect.H2)`로 테스트용 SQL을 생성하고, 프로덕션 방언(`POSTGRES` 등)으로도 SQL 문자열을 검증하여 방언 간 차이를 확인한다.

### 2.2. JPA 통합 테스트

- **Repository 테스트**: `JpaIntegrationTestBase`를 상속한다.
- **Controller/Web 테스트**: `JpaWebIntegrationTestBase`를 상속한다.
- H2 데이터베이스를 로드한다.
- Terms, System 등 JPA 엔티티를 사용하는 도메인의 테스트에 해당한다.

## 3. base class 구조

| base class | 용도 | 로드 범위 |
|---|---|---|
| `JpaIntegrationTestBase` | JPA 통합 테스트 | H2 |
| `JpaWebIntegrationTestBase` | JPA Web 테스트 | H2 + MockMvc |

## 4. 테스트 데이터 생성

- 타임스탬프 기반 정렬이 필요한 테스트에서 `Thread.sleep`을 사용하지 않는다.
- 도메인별 `fixture` 패키지에 Fixture 클래스를 작성하여 명시적 타임스탬프를 지정한다 (예: `MessageFixture`).
- 랜덤/대량 데이터가 필요하면 Instancio(`Instancio.create(...)`, `Instancio.of(...)` 등)로 생성한다. Fixture를 대체하는 도구가 아니라, 검증과 무관한 필드를 채우는 보완 도구로 사용한다.
- 어서션 대상 필드와 도메인 불변 조건에 관여하는 필드는 랜덤에 맡기지 않고 Fixture 또는 커스터마이저(`set`/`supply`)로 명시적으로 고정한다. 어서션이 랜덤 값에 의존하면 안 된다.
- 랜덤 생성 테스트는 실패를 재현할 수 있어야 한다. 테스트 클래스에 `@ExtendWith(InstancioExtension::class)`를 부착하면 실패 시 seed가 리포트되며, `@Seed` 또는 `Instancio.of(...).withSeed(...)`로 해당 seed를 고정해 재현한다.

## 5. 경계값·파라미터라이즈드 테스트

- 경계값(최소/최대, 경계 ±1, 빈 값, 임계점)은 랜덤으로 뽑지 않고 명시적으로 고정한다. 랜덤 데이터는 경계값 커버리지를 보장하지 않는다.
- 같은 로직을 여러 입력으로 검증할 때는 테스트 메서드를 복사하지 않고 `@ParameterizedTest`(`@ValueSource`/`@CsvSource`/`@MethodSource`)로 케이스를 나열한다.
- 검증 규칙(길이, 범위, 패턴)이 있는 입력은 유효 경계와 무효 경계 양쪽 케이스를 모두 포함한다 (예: 길이 제한이 100이면 100은 성공, 101은 실패).

## 6. N+1 쿼리 카운트 검증

- QueryDSL/JPA 통합 테스트에서 N+1 문제 방지를 위해 쿼리 카운트를 어서션한다.
- Hibernate Statistics(`SessionFactory.getStatistics()`) 또는 DataSource 프록시(`datasource-proxy` 등)를 활용한다.
- 테스트 메서드 실행 전후로 쿼리 카운트를 비교하여 예상 쿼리 수를 초과하지 않는지 검증한다.

## 7. 금지 패턴

- 테스트 클래스에 `@SpringBootTest`, `@ActiveProfiles`, `@AutoConfigureMockMvc`를 직접 선언하지 않는다.
- 테스트에서 `Thread.sleep`으로 실행 순서나 타임스탬프 차이를 보장하지 않는다.
- 어서션 대상 필드나 경계값을 랜덤으로 생성하지 않는다. seed 재현 수단 없이 랜덤 데이터를 사용하지 않는다.
