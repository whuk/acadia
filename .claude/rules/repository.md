# Repository 계층 규칙

JPA Repository 작성 시 동적 검색 조건 처리 방식과 쿼리 작성 규칙을 정의한다.

## 1. 핵심 원칙

- 검색(조회) 조건이 동적으로 조합되는 경우, `@Query` 어노테이션을 사용하지 않고 **JPA Specification**을 사용한다.
- Repository 인터페이스는 `JpaSpecificationExecutor<T>`를 함께 상속하여 Specification 기반 조회를 지원한다.

## 2. Specification 사용 규칙

- 검색 조건별로 개별 Specification 메서드를 작성하고, `and()`, `or()` 등으로 조합하여 사용한다.
- Specification 메서드는 도메인별 전용 클래스에 `static` 메서드로 그룹화한다 (예: `OrderSpecifications`, `UserSpecifications`).
- null이거나 빈 값인 조건은 무시하도록 처리하여, 조건의 유무에 따라 동적으로 쿼리가 구성되도록 한다.

## 3. @Query 허용 범위

- Specification으로 표현하기 어려운 **집계(aggregation)**, **서브쿼리**, **네이티브 쿼리** 등 특수한 경우에만 예외적으로 `@Query`를 허용한다.
- Spring Data JPA의 메서드 이름 기반 쿼리 파생(Query Derivation)은 단순 조회(단일 조건, 2개 이하 조건)에 한해 허용한다.

## 4. 금지 패턴

- 동적 검색 조건을 `@Query` 어노테이션의 JPQL/HQL 문자열로 직접 작성하지 않는다.
- 검색 조건 분기를 위해 여러 개의 `@Query` 메서드를 나열하지 않는다.
- Specification 클래스 내부에 비즈니스 로직을 포함하지 않는다. 순수한 조건 조립만 담당한다.

## 5. 도구 선택 계층 (Escalation Ladder)

항상 최하위 충분한 단계에서 시작한다. 상위 단계로 올라가려면 구체적인 근거가 필요하다.

| Level | 도구 | 사용 조건 |
|-------|------|-----------|
| 0 | Query Derivation | 정적 조건 2개 이하의 단순 조회 |
| 1 | JPA Specification | 동적 검색 조건 조합 (기본값, 섹션 1~4) |
| 2 | QueryDSL | N+1 fetch join + 페이지네이션, 3개 이상 엔티티 조인, 타입 안전 DTO 프로젝션, 복잡한 서브쿼리 |
| 3 | JdbcClient + jOOQ | 대량 벌크 처리, 리포팅/집계(GROUP BY, 윈도우 함수, CTE), 측정된 JPA 성능 병목, JPQL/QueryDSL로 표현 불가한 네이티브 SQL 기능 |

- Level 2는 Specification으로 표현이 어렵거나 N+1 문제가 발생하는 경우에 사용한다.
- Level 3은 **측정된 성능 문제**가 있을 때만 사용한다. 선제적 사용을 금지한다.
- 동일 도메인에서 여러 Level을 혼용할 수 있다. 쿼리별로 적절한 Level을 선택한다.

## 6. QueryDSL 사용 규칙

### 6.1. 의존성

- `io.github.openfeign.querydsl` 6.x를 사용한다 (Jakarta EE / Spring Boot 3+ 네이티브 지원).
- 원본 `com.querydsl` 5.1.0은 사실상 관리 중단 상태이므로 신규 프로젝트에서 사용하지 않는다.
- Q-class는 빌드 시 자동 생성한다. 버전 관리(VCS)에 포함하지 않는다.

### 6.2. 클래스 구조

Spring Data의 Custom Repository Fragment 패턴을 사용한다.

- `{Domain}RepositoryCustom` — 커스텀 메서드를 선언하는 인터페이스
- `{Domain}RepositoryImpl` — QueryDSL로 구현하는 클래스
- `{Domain}Repository`가 `JpaRepository`, `JpaSpecificationExecutor`, `{Domain}RepositoryCustom`을 모두 상속한다.
- 세 클래스 모두 같은 패키지에 배치한다.

```java
public interface OrderRepositoryCustom {
    List<OrderReadDto> findOrdersWithItems(OrderSearchQuery query);
}

public class OrderRepositoryImpl implements OrderRepositoryCustom {
    private final JPAQueryFactory queryFactory;
    // QueryDSL 구현
}

public interface OrderRepository extends
        JpaRepository<OrderEntity, Long>,
        JpaSpecificationExecutor<OrderEntity>,
        OrderRepositoryCustom {
}
```

- `JPAQueryFactory`는 `@Configuration` 클래스에서 단일 빈으로 등록한다.

### 6.3. DTO 프로젝션

- `Projections.constructor()` 또는 `@QueryProjection`으로 Read DTO(`record`)에 직접 매핑한다.
- `layer-communication-rules.md` 3.2절의 Query 흐름과 일관되게, 단순 조회 시 Rich Domain 객체를 거치지 않는다.

### 6.4. N+1 방지 도구

우선순위 순서대로 적용한다.

1. **`@EntityGraph`**: 단순 단일 depth 즉시 로딩에 사용한다. Repository 메서드에 `@EntityGraph(attributePaths = {...})`를 선언한다.
2. **QueryDSL `fetchJoin()`**: 다중 depth 또는 조건부 조인이 필요한 경우 사용한다.
3. **`@BatchSize` / `hibernate.default_batch_fetch_size`**: 컬렉션 로딩의 글로벌 폴백으로 설정한다.
4. **DTO Projection**: 엔티티 그래프 자체를 우회한다. Read 전용 Query 흐름에 가장 적합하다.

### 6.5. 금지 패턴

- Specification으로 충분한 단순 동적 필터에 QueryDSL을 사용하지 않는다.
- Service/Finder 클래스에 QueryDSL 쿼리 로직을 배치하지 않는다. `RepositoryImpl`에만 위치시킨다.
- Finder에서 `JPAQueryFactory`를 직접 주입하지 않는다. Repository 인터페이스를 통해서만 접근한다.
- `fetchJoin()`과 `offset/limit` 페이지네이션을 조합하지 않는다 (인메모리 페이지네이션 경고 발생). DTO 프로젝션 또는 서브쿼리 기반 페이지네이션을 사용한다.

## 7. JdbcClient 사용 규칙

### 7.1. 전제 조건

- Spring Boot 3.2+ / Spring Framework 6.1+ 환경에서 사용한다.
- `spring-boot-starter-jdbc` 또는 `spring-boot-starter-data-jdbc` 의존성이 필요하다.

### 7.2. 클래스 구조

- `{Domain}JdbcRepository` — `@Repository` 애노테이션을 붙인 독립 클래스. JPA Repository의 확장이 아니다.
- JPA Repository와 같은 패키지에 배치한다.
- `JdbcClient`를 주입받아 사용한다. `JdbcTemplate`이나 `NamedParameterJdbcTemplate`을 직접 주입하지 않는다.

```java
@Repository
public class OrderJdbcRepository {
    private final JdbcClient jdbcClient;

    public OrderJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }
}
```

### 7.3. SQL 관리

- 5줄 이하의 단순 쿼리: 인라인 문자열을 허용한다.
- 5줄 초과의 쿼리: `resources/sql/{domain}/{operation}.sql` 파일로 외부화한다 (예: `resources/sql/order/monthly-summary.sql`).

### 7.4. Row 매핑

- `RowMapper<T>` 구현체 또는 람다를 사용하여 Read DTO(`record`)에 직접 매핑한다.
- `layer-communication-rules.md` 3.2절과 일관되게, 도메인 객체를 경유하지 않는다.

### 7.5. jOOQ를 SQL 빌더로 사용 (DB 벤더 독립성)

jOOQ를 **SQL 생성 전용**으로 사용하여 데이터베이스 벤더 종속성을 제거한다. jOOQ는 쿼리를 실행하지 않는다.

**클래스 구조**:
- `{Domain}SqlBuilder` — `@Component` 클래스에 SQL 생성 로직을 배치한다.

**실행 패턴**:
```java
@Component
public class OrderSqlBuilder {
    private final DSLContext dsl;

    public OrderSqlBuilder(DSLContext dsl) {
        this.dsl = dsl;
    }

    public SqlWithParams monthlySummary(YearMonth month) {
        var query = dsl.select(...)
            .from(table("orders"))
            .where(field("created_at").greaterOrEqual(month.atDay(1)))
            .groupBy(field("status"));

        return new SqlWithParams(
            query.getSQL(ParamType.NAMED),
            query.getBindValues()
        );
    }
}
```

**DSLContext 설정**:
- `DataSource` 없이 빌더 전용으로 구성한다: `DSL.using(SQLDialect.POSTGRES)`.
- `@Configuration` 클래스에서 단일 빈으로 등록한다.

```java
@Configuration
public class JooqConfig {
    @Bean
    public DSLContext dslContext() {
        return DSL.using(SQLDialect.POSTGRES);
    }
}
```

**사용 범위**:
- 다중 DB 벤더를 지원하거나 복잡한 쿼리에서 문자열 조합이 오류를 유발할 수 있는 경우 jOOQ SqlBuilder를 사용한다.
- 단일 DB 프로젝트의 단순 쿼리는 plain SQL 문자열을 허용한다.
- jOOQ 코드 생성(테이블 메타데이터 클래스)은 선택사항이다. SQL 빌더 전용 사용 시 문자열 테이블/컬럼명으로 충분하다. 코드 생성을 사용하는 경우, 생성된 클래스는 빌드 출력 디렉토리에 배치하고 버전 관리에 포함하지 않는다.

### 7.6. 금지 패턴

- JPA로 충분한 표준 CRUD에 JdbcClient를 사용하지 않는다.
- jOOQ에서 `query.fetch()` 등으로 쿼리를 직접 실행하지 않는다. 실행은 JdbcClient만 담당한다.
- `DSLContext`에 `DataSource`를 주입하지 않는다. 빌더 전용 인스턴스만 사용한다.
- Finder/Service 클래스에 SQL 생성 로직을 배치하지 않는다. `{Domain}SqlBuilder` 또는 `{Domain}JdbcRepository`에만 위치시킨다.
- 쓰기(Command) 흐름에서 도메인 검증을 우회하기 위해 JdbcClient를 사용하지 않는다. 벌크 작업은 예외이며, 도메인 검증 우회 사유를 명시적으로 문서화한다.

## 8. Finder/Service 계층과의 통합

`service-layer.md`의 Finder/Service 분리 규칙에 따라 다음과 같이 통합한다.

```
Finder/Service
    |
    +---> {Domain}Repository (JPA — Specification + QueryDSL)
    |         |
    |         +---> {Domain}RepositoryCustom → {Domain}RepositoryImpl
    |
    +---> {Domain}JdbcRepository (성능 최적화 쿼리)
              |
              +---> {Domain}SqlBuilder (jOOQ, 선택)
```

- **Finder** (조회 전용): 모든 티어의 Repository를 사용할 수 있다. 내부적으로 어떤 티어가 사용되는지 알 필요 없다.
- **Service** (상태 변경): 주로 JPA `{Domain}Repository`를 사용한다. JdbcClient는 벌크 작업 등 예외적 경우에만 허용하며, 도메인 검증 우회를 명시적으로 문서화한다.
- **의존 방향**: Finder/Service → Repository/JdbcRepository. 역방향 의존을 금지한다. SqlBuilder는 JdbcRepository의 내부 의존성이며, Finder/Service에서 직접 사용하지 않는다.

## 9. 테스트

- **QueryDSL RepositoryImpl 테스트**: `JpaIntegrationTestBase`를 상속한다 (기존 JPA Repository 테스트와 동일).
- **JdbcClient Repository 테스트**: `JpaIntegrationTestBase`를 상속한다. 동일한 DataSource(H2)를 사용한다.
- **jOOQ SqlBuilder 테스트**: 순수 단위 테스트로 작성한다. Spring 컨텍스트를 로드하지 않는다. `DSL.using(SQLDialect.H2)`로 테스트용 SQL을 생성하고, 프로덕션 방언과의 차이는 방언별 SQL 문자열 검증으로 확인한다.
- **N+1 검증**: QueryDSL/JPA 통합 테스트에서 Hibernate Statistics 또는 DataSource 프록시(`datasource-proxy` 등)를 사용하여 쿼리 카운트를 어서션한다.
