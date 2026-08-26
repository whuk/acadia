---
description: "claude-code-base 템플릿을 실제 프로젝트 스택에 맞게 선별 적용하고, 해당하지 않는 rules/agents 파일을 정리한다. 기존 코드가 있으면 스택을 자동 감지하고, 빈 프로젝트면 질문한다."
argument-hint: "없음"
user-invocable: true
---

# /rw:init — 템플릿 선별 초기화

claude-code-base 템플릿을 실제 프로젝트 스택에 맞게 선별 적용한다. 프로젝트 루트를 스캔해 기존 코드가 있으면 스택을 자동 감지하고, 감지하지 못한 항목(빈 프로젝트면 전체)만 질문한 뒤, 확정된 스택에 맞지 않는 `.claude/rules/`와 `.claude/agents/` 파일을 제거하거나 정리한다.

## 0단계: 안전 확인

1. `git remote -v`를 확인한다. 원격 저장소 이름에 `claude-code-base`가 포함돼 있으면 다음 메시지로 사용자에게 확인을 구한다(중단하지는 않되, 실수로 원본 템플릿에서 실행하는 상황을 막기 위함이다):
   > 이 저장소가 claude-code-base 템플릿 원본처럼 보입니다. 원본이 맞다면 실행을 중단해 주세요. 이 템플릿을 복사해서 만든 새 프로젝트라면 계속 진행해도 됩니다.
2. `.claude/rules/backend/`와 `.claude/rules/frontend/` 중 어느 것도 존재하지 않으면 다음 메시지를 출력하고 **중단**한다:
   > 이 저장소에는 claude-code-base 템플릿의 rules 디렉토리가 없습니다. 템플릿을 먼저 복사해 주세요.
3. `git status --short`로 커밋되지 않은 변경사항이 있으면 사용자에게 알리고, 먼저 커밋하거나 stash 할지 확인한다. 이번 작업의 삭제/rename 내역이 기존 변경사항과 섞이지 않도록 한다.

## 1단계: 프로젝트 스캔 (자동 감지)

프로젝트 루트에서 스택 마커 파일을 찾는다: `build.gradle`/`build.gradle.kts`/`pom.xml`(Spring Boot), `package.json`(NestJS/프론트엔드), `pyproject.toml`/`requirements*.txt`(FastAPI). 모노레포 가능성이 있으므로 루트 직속과 1~2 depth 하위 디렉토리(`apps/*`, `packages/*`, `frontend/`, `backend/` 등)까지 확인한다. `.claude/`, `node_modules/`, 빌드 출력 디렉토리는 스캔 대상이 아니다.

- **마커 파일이 하나도 없으면**(빈 템플릿 상태) 자동 감지를 건너뛰고 2단계에서 모든 질문을 진행한다.
- **마커 파일이 있으면** 아래 감지 규칙으로 2단계 질문들의 답을 먼저 확정한다. 명확한 근거(파일 경로, 의존성 이름, 디렉토리 구조)가 있는 항목만 확정하고 항목별 근거를 기록한다. 근거가 없거나 상충하는 항목은 미확정으로 남겨 2단계에서 질문한다.

### 영역·백엔드 스택·프론트엔드 프레임워크 감지
- Spring Boot: 빌드 파일에 `org.springframework.boot` 플러그인/의존성
- NestJS: `package.json`의 의존성에 `@nestjs/core`
- FastAPI: `pyproject.toml`/`requirements*.txt`에 `fastapi`
- 프론트엔드: `package.json`의 의존성에 `next` → Next.js / `vue` → Vue.js / `next` 없이 `vite` + `react` → Vite
- 백엔드와 프론트엔드 마커가 모두 발견되면 풀스택으로 확정한다. 한쪽만 발견됐으면 다른 쪽 영역의 사용 여부는 미확정으로 남겨 라운드 1 질문으로 확인한다(아직 코드를 만들지 않았을 뿐 계획에 있을 수 있다).

### Spring Boot 세부 감지
- 언어: `src/main/kotlin` 존재 또는 Kotlin JVM 플러그인 → Kotlin / `src/main/java`만 존재 → Java
- 웹 스택: `spring-boot-starter-webflux`만 있고 `spring-boot-starter-web`이 없으면 WebFlux / `spring-boot-starter-web`이 있으면 MVC. **단, 언어가 Kotlin인데 WebFlux가 감지되면 확정하지 말고 "미지원 조합"으로 표시해 2단계 라운드 3에서 확인한다** — 이 템플릿의 WebFlux 규칙(`spring/java/webflux.md`·`repository-r2dbc.md`·`repository-reactive-mongo.md`)은 Java 전용이라 Kotlin 대응 파일이 없다. 감지 결과를 조용히 MVC로 덮어쓰지 않는다
- 영속성 도구: `spring-boot-starter-data-jpa` → JPA / JPA 없이 `spring-boot-starter-jdbc`(+`jooq`) → SQL-first / `spring-boot-starter-data-r2dbc` → WebFlux 감지 결과와 교차 확인해 R2DBC로 확정
- 아키텍처 스타일: 소스 패키지에 `port/in`·`port/out`·`adapter` 디렉토리 또는 `*UseCase`/`*Port`/`*PersistenceAdapter` 클래스 → Hexagonal / controller·service·repository·domain 계층 패키지 구조 → Layered. 소스가 거의 없어 구조 판정이 어려우면 미확정
- 헥사고날 flavor (Hexagonal + JPA + MVC일 때만, 언어 Java/Kotlin 모두): 도메인 클래스에 `@Entity`가 붙어 있고 `application/**/provided`·`required` 패키지 또는 `@ApplicationService`/`@WebApiAdapter` stereotype이 있으며 `openapi.yaml`이 없으면 → Pragmatic / `port/in`·`port/out` 패키지와 `*PersistenceAdapter`·`*JpaEntity` 클래스, `openapi.yaml`이 있으면 → Clean. 근거가 없으면 미확정. (WebFlux·SQL-first면 flavor를 감지하지 않고 Clean으로 고정 — Pragmatic은 Hexagonal+JPA+MVC 전용. Kotlin은 항상 MVC이므로 Hexagonal+JPA면 대상이다.)
- MongoDB: `spring-boot-starter-data-mongodb`(WebFlux면 `-reactive`) 존재 여부로 확정
- QueryDSL/jOOQ 계획: `querydsl`/`jooq` 의존성이 이미 있으면 "쓸 계획 있음"으로 확정. 없으면 미확정(도입 계획은 코드로 감지할 수 없다)
- 주 RDB: JDBC/R2DBC 드라이버 의존성(`postgresql`/`mysql`/`mariadb`/`sqlite` 등) 또는 `application.yml`·`application.properties`의 datasource/r2dbc URL 스킴

### NestJS 세부 감지
- 영속성 도구: `typeorm`/`@nestjs/typeorm` → TypeORM / `prisma`/`@prisma/client` → Prisma / ORM 없이 `kysely` → SQL-first
- 검증 도구: `nestjs-zod` 존재(또는 `class-validator` 없이 `zod`) → Zod / `class-validator` → class-validator
- (풀스택) 프론트엔드 소스 루트: 프론트엔드 프레임워크 의존성이 든 `package.json`이 위치한 디렉토리 경로

### FastAPI 세부 감지
- ORM 사용 여부: `sqlalchemy` 의존성 + ORM 매핑 사용 흔적(`DeclarativeBase`/`Mapped`, `models.py`) → ORM 사용 / `sqlalchemy` 없이 async 드라이버만 쓰거나 Core(`Table`/`text()`)만 사용 → SQL-first. `sqlalchemy`가 있어도 ORM 매핑 흔적이 없으면 미확정
- 주 RDB: 드라이버 의존성 — `asyncpg`/`psycopg` → PostgreSQL / `asyncmy`/`aiomysql`/`PyMySQL` → MySQL / `aiosqlite` → SQLite

### Vite 세부 감지
- 라우팅 라이브러리: `@tanstack/react-router` → TanStack Router / `react-router`/`react-router-dom` → React Router

### 언어·프레임워크 버전 감지 (스택 공통)

확정된 스택에 해당하는 항목만 감지한다. 여러 소스가 서로 다른 값을 가리키면(예: `.java-version`은 17인데 Gradle 툴체인은 21) 미확정으로 남겨 2단계에서 질문한다.

- **JDK**: Gradle의 `JavaLanguageVersion.of(N)`(toolchain) / `sourceCompatibility`·`targetCompatibility`, Maven의 `maven.compiler.release`·`maven.compiler.source`·`<java.version>` property, `.java-version`(jenv), `.sdkmanrc`, `.tool-versions`(asdf/mise)의 `java` 항목
- **Kotlin**: 빌드 파일의 `kotlin("jvm") version "..."`/`org.jetbrains.kotlin.jvm` 플러그인 버전, `kotlin.jvmToolchain(N)`(JDK 쪽 근거로도 사용)
- **Spring Boot**: `org.springframework.boot` 플러그인 버전(Gradle) 또는 `spring-boot-starter-parent`/`spring-boot-dependencies` BOM 버전(Maven)
- **Python**: `pyproject.toml`의 `requires-python`, `.python-version`(pyenv), `.tool-versions`의 `python` 항목, Docker 베이스 이미지 태그
- **FastAPI·Pydantic**: `pyproject.toml`/`requirements*.txt`의 `fastapi`·`pydantic` 버전 지정, lock 파일(`poetry.lock`/`uv.lock`/`requirements.lock`)의 해결된 버전. 버전 지정이 없거나(`fastapi`만 적힌 경우) 범위가 너무 넓어 v1/v2 판정이 불가하면 미확정
- **Node**: `package.json`의 `engines.node`, `.nvmrc`, `.tool-versions`의 `nodejs` 항목
- **Vue**: `package.json`의 `vue` 의존성 버전(`^3.x` → Vue 3 / `^2.x` → Vue 2)

이 항목들은 삭제/rename 대상을 바꾸지 않는다(3단계의 "언어·프레임워크 버전 — 보고만" 참조). 감지 목적은 rules가 전제하는 최소 버전을 충족하는지 확인해 보고하기 위함이다.

## 2단계: 스택 질문 (AskUserQuestion 사용, 여러 라운드로 진행)

1단계에서 확정하지 못한 항목만 묻는다. 확정된 항목의 질문은 생략하고, 그 답이 뒤 라운드의 분기 조건이면 확정된 답을 그대로 사용한다. 모든 항목이 확정됐으면 이 단계 전체를 건너뛴다.

**라운드 1 — 영역이 미확정이면 묻는다**: "이 프로젝트는 어떤 영역을 사용합니까?"
- 백엔드만
- 프론트엔드만
- 풀스택 (백엔드 + 프론트엔드)

**라운드 2 — 라운드 1 답변에 따라 해당하는 것만 함께 묻는다**:
- (백엔드 포함 시) "백엔드 스택은 무엇입니까?" → Spring Boot(Java 또는 Kotlin) / NestJS(TypeScript) / FastAPI(Python)
- (프론트엔드 포함 시) "프론트엔드 프레임워크는?" → Next.js / Vite / Vue.js

**라운드 3 — 라운드 2 답변에 따라 해당하는 것만 묻는다**:

(백엔드 스택 "Spring Boot" 선택 시)
- "언어는 무엇입니까?" → Java(기본) / Kotlin
- "아키텍처 스타일은 무엇입니까?" → Layered(기본) / Hexagonal(Ports & Adapters)
- (아키텍처 "Hexagonal" + 영속성 "JPA" + 웹 스택 "Spring MVC"인 경우에만, 언어 Java/Kotlin 모두) "헥사고날 flavor는 무엇입니까?" → Clean(엄격, 기본) / Pragmatic(실용). Pragmatic은 도메인=JPA 엔티티(orm.xml)·`provided`/`required` 역할 포트·Spring Data 리포지토리를 드리븐 포트로·서비스/컨트롤러의 Domain 반환·애그리거트 간 객체 참조(읽기 전용)·code-first 웹·애플리케이션 서비스 통합 테스트·ArchUnit을 한 세트로 묶은 실용 헥사고날이다. WebFlux·SQL-first 조합에서는 이 질문을 하지 않고 Clean으로 고정한다(Pragmatic 변형은 Hexagonal+JPA+MVC 전용). Kotlin은 항상 Spring MVC이므로 Hexagonal+JPA면 이 질문을 한다.
- (언어 "Java" 선택 시) "웹 스택은 무엇입니까?" → Spring MVC(서블릿, 기본) / WebFlux(리액티브)
- (언어 "Kotlin" 선택 시) WebFlux 규칙은 현재 Java 전용이므로 원칙적으로 묻지 않고 MVC로 간주한다. **단, 1단계에서 "미지원 조합"(Kotlin + WebFlux)으로 표시됐으면 MVC로 간주하지 말고 다음을 묻는다**: "이 프로젝트는 Kotlin + WebFlux 조합인데, 이 템플릿에는 Kotlin용 WebFlux/R2DBC 규칙이 없습니다. 어떻게 진행할까요?"
  - 중단하고 스택을 다시 확인한다(권장) → 이 경우 3단계로 넘어가지 않고 **중단**한다
  - MVC 기준으로 계속 진행한다 → 남는 규칙은 블로킹(JPA/MVC) 전제이므로 리액티브 코드에는 맞지 않는다는 점을 옵션 설명에 명시하고, 선택 시 3단계의 "언어 Kotlin + WebFlux 감지" 절을 적용한다
- (웹 스택이 "Spring MVC"인 경우에만) "영속성 도구는 무엇입니까?" → JPA(ORM, 기본) / SQL-first(JdbcClient + jOOQ, ORM 미사용). WebFlux 선택 시 영속성은 R2DBC로 고정되므로 이 질문과 아래 JPA 세부 질문 2개를 묻지 않는다
- (영속성 도구 "JPA" 선택 시) "MongoDB도 함께 사용합니까?" → JPA만 사용(기본) / JPA + MongoDB 함께 사용
- (영속성 도구 "JPA" 선택 시) "Repository 조회 도구로 QueryDSL/jOOQ까지 쓸 계획이 있습니까?" → Specification까지만 사용(기본) / QueryDSL/jOOQ까지 쓸 계획 있음
- (웹 스택 "WebFlux" 선택 시) "리액티브 MongoDB도 함께 사용합니까?" → R2DBC만 사용(기본) / R2DBC + 리액티브 MongoDB 함께 사용
- "주 관계형 데이터베이스(RDB)는 무엇입니까?" → PostgreSQL(기본) / MySQL / MariaDB / 그 외 유명 DB(Oracle·SQL Server·SQLite 등, "Other"로 직접 입력). 옵션 설명에 PostgreSQL/MySQL이 가장 자주 사용되는 선택지임을 표기한다. 웹 스택과 무관하게 묻는다. 이 답변은 파일 편집으로 이어지지 않는다 — 규칙의 jOOQ 방언/R2DBC 드라이버 예시는 PostgreSQL 기준으로 고정돼 있으며, 선택 결과는 결과 보고에서 사용자가 직접 교체하도록 안내하는 용도로만 쓴다.

(백엔드 스택 "NestJS" 선택 시)
- "영속성 도구는 무엇을 사용합니까?" → TypeORM / Prisma / 사용 안 함(SQL-first, Kysely)
- "입력 검증 도구는 무엇을 사용합니까?" → class-validator(기본) / Zod(nestjs-zod)
- RDB 질문은 하지 않는다 — `nestjs.md`에 DB 벤더 종속 내용이 없어 답변이 편집으로 이어지지 않는다.

(백엔드 스택 "FastAPI" 선택 시)
- "SQLAlchemy ORM을 사용합니까?" → ORM 사용(기본) / SQL-first(Core/async 드라이버만, ORM 미사용)
- "주 관계형 데이터베이스(RDB)는 무엇입니까?" → PostgreSQL(기본) / MySQL / MariaDB / 그 외 유명 DB(SQLite 등, "Other"로 직접 입력). 옵션 설명에 PostgreSQL/MySQL이 가장 자주 사용되는 선택지임을 표기한다. 이 답변은 파일 편집으로 이어지지 않는다 — `fastapi.md`의 드라이버 예시는 asyncpg(PostgreSQL) 기준으로 고정돼 있으며, 선택 결과는 결과 보고에서 사용자가 직접 교체하도록 안내하는 용도로만 쓴다.

(프론트엔드 "Vite" 선택 시)
- "라우팅 라이브러리는 무엇을 사용합니까?" → TanStack Router(기본 검토 대상) / React Router

(풀스택 + 백엔드 "NestJS" 선택 시)
- "프론트엔드 소스 루트 경로는 무엇입니까?" (예: `apps/web`, `frontend`, `web`) — 백엔드와 프론트엔드가 모두 TypeScript이라 `frontend/*.md`의 `paths` 패턴(`**/*.ts`)이 백엔드 소스에도 매칭될 수 있다. 이 경로는 파일 편집에 쓰지 않고, 결과 보고에서 "필요하면 이 경로로 `paths`를 직접 좁히라"는 안내에만 사용한다 (3단계의 해당 안내 규칙 참조)

Next.js/Vue.js를 선택한 경우 프론트엔드 세부 질문은 없다.

**라운드 4 — 언어·프레임워크 버전 (1단계에서 감지하지 못한 항목만)**:

확정된 스택에 해당하는 것만 묻는다. 1단계에서 감지된 항목은 묻지 않는다. 이 답변들은 파일 편집으로 이어지지 않고 전제 검증·보고에만 쓰인다(3단계의 "언어·프레임워크 버전 — 보고만" 참조). 각 질문의 옵션 설명에 "이 답변은 파일 삭제 대상을 바꾸지 않으며, 규칙이 전제하는 최소 버전 충족 여부 확인에만 쓰입니다"를 표기한다.

(백엔드 "Spring Boot" + 언어 "Java" 선택 시)
- "JDK 버전은 무엇입니까?" → 21(LTS, 기본) / 17(LTS, 규칙이 요구하는 최소) / 25(LTS) / 그 외("Other"로 직접 입력)
- "Spring Boot 버전대는 무엇입니까?" → 3.2 이상(기본) / 3.0~3.1 / 2.x. 옵션 설명에 각각의 의미를 표기한다 — 3.2+는 `JdbcClient`를 쓸 수 있는 기준선, 3.0~3.1은 Jakarta EE는 맞지만 `JdbcClient`가 없음, 2.x는 `javax.persistence` 시대라 규칙 전반의 Jakarta 전제가 깨짐

(백엔드 "Spring Boot" + 언어 "Kotlin" 선택 시)
- "JDK 버전은 무엇입니까?" → (Java와 동일한 선택지)
- "Kotlin 버전대는 무엇입니까?" → 2.x(기본) / 1.9.x / 그 외
- "Spring Boot 버전대는 무엇입니까?" → (Java와 동일한 선택지)

(백엔드 "FastAPI" 선택 시)
- "Python 버전은 무엇입니까?" → 3.13(기본) / 3.12 / 3.11 / 그 외("Other"로 직접 입력). 옵션 설명에 규칙이 요구하는 최소는 3.9(Pydantic v2·최신 FastAPI 기준)임을 표기한다
- "FastAPI·Pydantic 버전대는 무엇입니까?" → FastAPI 0.100 이상 + Pydantic v2(기본) / FastAPI 0.99 이하 + Pydantic v1 / 그 외("Other"로 직접 입력). 옵션 설명에 `fastapi.md` 2번의 다중 진입점 방어가 Pydantic v2의 인스턴스화 시점 검증을 근거로 삼는다는 점, FastAPI가 Pydantic v2를 지원하기 시작한 것이 0.100이라는 점을 표기한다

(백엔드 "NestJS" 선택 시 또는 프론트엔드 포함 시)
- "Node.js 버전대는 무엇입니까?" → 22 LTS(기본) / 20 LTS / 24 LTS / 그 외. 백엔드·프론트엔드 모두 Node를 쓰는 풀스택이면 한 번만 묻는다(런타임이 하나로 통일된 경우). 서로 다른 버전을 쓰면 "Other"로 각각 기입하도록 안내한다

(프론트엔드 "Vue.js" 선택 시)
- "Vue 버전은 무엇입니까?" → 3.x(기본) / 2.x. 옵션 설명에 `vue.md`가 Vue 3 + Composition API(`<script setup>`)를 전제로 한다는 점을 표기한다

NestJS·Next.js·Vite·React 자체의 라이브러리 버전은 묻지 않는다. 해당 rules에 버전 의존적인 최소 전제가 없어 답변이 검증으로 이어지지 않기 때문이다(전제가 있는 항목만 묻는다는 원칙).

## 3단계: 삭제/rename 대상 확정

확정된 답(1단계 자동 감지 + 2단계 질문 답변)에 따라 아래 규칙을 조합해 대상 목록을 만든다. 확정되지 않은 조합을 추측해서 임의로 처리하지 않는다.

### 백엔드 미포함 시 — 삭제
- `.claude/rules/backend/` 전체
- 모든 백엔드 에이전트: `.claude/agents/spring-*.md`(spring-hexagonal-* 포함 14개), `.claude/agents/nestjs-*.md`(7개), `.claude/agents/fastapi-*.md`(7개)

### 백엔드 포함 + "Spring Boot" 선택 시 — 삭제
- `.claude/rules/backend/nestjs/`, `.claude/rules/backend/fastapi/` 전체 (`spring/`, `shared/`는 유지)
- `.claude/agents/nestjs-*.md`(7개), `.claude/agents/fastapi-*.md`(7개)

### 백엔드 스택 "Spring Boot" + 언어 "Java" 선택 시 — 삭제
- `.claude/rules/backend/spring/kotlin/` 전체 (`spring/java/`, `spring/api-dto.md`는 유지)

### 백엔드 스택 "Spring Boot" + 언어 "Kotlin" 선택 시 — 삭제
- `.claude/rules/backend/spring/java/` 전체 (`spring/kotlin/`, `spring/api-dto.md`는 유지). Kotlin은 `spring/kotlin/`의 hexagonal Pragmatic 변형과 `spring/kotlin/archunit.md`를 사용한다. 언어 공통인 `spring/api-code-first.md`는 flavor/아키텍처 절에서 처리한다(여기서 삭제하지 않는다).

### 백엔드 스택 "Spring Boot" + 아키텍처 스타일 "Layered" 선택 시 — 삭제
- 선택한 언어 디렉토리의 `.claude/rules/backend/spring/{java|kotlin}/hexagonal/` 전체 (`spring/{java|kotlin}/layered/`, 아키텍처 공통인 `spring/{java|kotlin}/repository-tools.md`, `spring/api-dto.md`는 유지. 아키텍처 공통인 `spring/{java|kotlin}/archunit.md`도 유지 — ArchUnit은 Layered에도 적용된다. `hexagonal/` 삭제에 Pragmatic 변형 파일도 함께 포함된다)
- `.claude/rules/backend/spring/api-code-first.md` (코드-first 웹 계층은 Pragmatic(Hexagonal) flavor 전용이다)
- Hexagonal 전담 에이전트 6개: `.claude/agents/spring-hexagonal-*.md`

### 백엔드 스택 "Spring Boot" + 아키텍처 스타일 "Hexagonal" 선택 시 — 삭제
- 선택한 언어 디렉토리의 `.claude/rules/backend/spring/{java|kotlin}/layered/` 전체 (`spring/{java|kotlin}/hexagonal/`, 아키텍처 공통인 `spring/{java|kotlin}/repository-tools.md`, `spring/api-dto.md`는 유지. 아키텍처 공통인 `spring/{java|kotlin}/archunit.md`도 유지)
- Layered 전제 에이전트 6개: `.claude/agents/spring-domain-designer.md`, `spring-tdd-implementer.md`, `spring-refactorer.md`, `spring-test-author.md`, `spring-code-reviewer.md`, `spring-debugger.md` (`spring-hexagonal-*` 6개와, 아키텍처 스타일과 무관한 `spring-style-checker`는 유지. `spring-openapi-spec-author`는 아키텍처가 아니라 **flavor**에 따라 갈리므로 여기서 판단하지 말고 아래 flavor 절에서 처리한다)

### 백엔드 스택 "Spring Boot" + 아키텍처 "Hexagonal" + JPA + MVC + flavor 선택 시 — 삭제/rename

Hexagonal + JPA + MVC일 때 선택한 언어 디렉토리(`spring/{java|kotlin}/`)의 `hexagonal/`에는 Clean 파일과 Pragmatic 변형 파일이 함께 들어 있다. flavor에 맞는 한 벌만 남긴다(Java·Kotlin 동일하게 적용). flavor를 묻지 않은 조합(WebFlux·SQL-first)은 자동으로 **Clean**으로 간주해 아래 'Clean 선택' 처리를 적용한다. 아래 경로의 `{java|kotlin}`은 선택한 언어로 치환한다.

- **Clean 선택 시**: Pragmatic 변형 파일을 `git rm`한다 — `spring/{java|kotlin}/hexagonal/domain-entity.md`, `hexagonal/ports-and-adapters-pragmatic.md`, `hexagonal/repository-pragmatic.md`, `hexagonal/service-layer-pragmatic.md`, `hexagonal/test-pragmatic.md`, `spring/api-code-first.md`. Clean 정규 파일(`domain.md`·`ports-and-adapters.md`·`repository.md`·`service-layer.md`·`test.md`)과 `spring/api-dto.md`, `spring/{java|kotlin}/archunit.md`는 유지한다.
- **Pragmatic 선택 시**: Clean 정규 파일을 `git rm`한 뒤 Pragmatic 변형을 `git mv`로 정규 이름에 맞춰 rename한다(본문 무편집):
  - `git rm spring/{java|kotlin}/hexagonal/domain.md` → `git mv .../domain-entity.md .../domain.md`
  - `git rm .../ports-and-adapters.md` → `git mv .../ports-and-adapters-pragmatic.md .../ports-and-adapters.md`
  - `git rm .../repository.md` → `git mv .../repository-pragmatic.md .../repository.md`
  - `git rm .../service-layer.md` → `git mv .../service-layer-pragmatic.md .../service-layer.md`
  - `git rm .../test.md` → `git mv .../test-pragmatic.md .../test.md`
  - 웹 계층: `git rm spring/api-dto.md` (spec-first 제거), `spring/api-code-first.md`는 정규 이름 그대로 유지. 함께 `git rm .claude/agents/spring-openapi-spec-author.md`도 삭제한다 — 이 에이전트는 "이 프로젝트는 yaml-first"를 전제로 `api-dto.md`와 `openapi.yaml`을 다루므로, spec-first를 제거한 Pragmatic에서는 전제 자체가 사라진다(flavor를 읽어 동작하도록 일반화돼 있지 않다).
  - `spring/{java|kotlin}/archunit.md`는 유지한다.
- **`spring-hexagonal-*` 6개는 flavor로 삭제하지 않는다.** 이 6개는 살아남은 정규 규칙 파일을 읽어 flavor(Clean/Pragmatic)를 판정하도록 일반화돼 있다(언어·flavor 무관). 4단계 보고에서 선택한 flavor를 명시한다. flavor에 따라 처리가 갈리는 에이전트는 위의 `spring-openapi-spec-author` 하나뿐이다(Clean이면 유지, Pragmatic이면 삭제).
- (Pragmatic + MongoDB 병용 시) MongoDB 테스트 추가분(`hexagonal/test-mongodb.md`)에는 Pragmatic 전용 변형이 없다. `test-mongodb.md`는 유지하되, 4단계 보고에서 "MongoDB 통합 base class 표는 Clean 기준 서술이며 Pragmatic의 통합 테스트 관례(`test.md` 2.2)와 함께 읽어야 한다"고 안내한다.

### 백엔드 스택 "Spring Boot" + 언어 "Kotlin" + WebFlux 감지 시 — 미지원 조합 (MVC 규칙 세트 + 경고)

2단계 라운드 3에서 "MVC 기준으로 계속 진행"을 선택한 경우에만 여기까지 온다("중단"을 선택했으면 3단계를 실행하지 않는다).

- 파일 처리는 **언어 "Kotlin" + 웹 스택 "Spring MVC"와 완전히 동일하게** 적용한다. `spring/java/` 전체가 이미 삭제되므로 WebFlux 전용 규칙(`webflux.md`·`repository-r2dbc.md`·`repository-reactive-mongo.md`)은 별도 처리 없이 함께 사라지고, 아래의 "웹 스택 WebFlux 선택 시" 절은 적용하지 않는다(그 절의 경로는 모두 `spring/java/` 하위여서 Kotlin 프로젝트에는 존재하지 않는다).
- 영속성·MongoDB·QueryDSL 질문은 MVC 기준으로 진행한 답을 그대로 사용한다.
- **4단계 보고에서 반드시 경고한다**: 남은 규칙은 블로킹 스택(JPA/MVC) 전제이므로 리액티브 코드 작성에 그대로 적용할 수 없다. Kotlin용 WebFlux/R2DBC 규칙을 쓰려면 `spring/java/webflux.md`·`repository-r2dbc.md`·`repository-reactive-mongo.md`를 Kotlin으로 옮겨 직접 작성해야 한다.

### 백엔드 스택 "Spring Boot" + 언어 "Java" + 웹 스택 "Spring MVC" 선택 시 — 삭제
- `.claude/rules/backend/spring/java/webflux.md`, `.claude/rules/backend/spring/java/repository-r2dbc.md`, `.claude/rules/backend/spring/java/repository-reactive-mongo.md` (WebFlux 전용 규칙. 언어 "Kotlin" 선택 시에는 `spring/java/` 전체 삭제에 이미 포함되므로 별도 처리가 없다)

### 백엔드 스택 "Spring Boot" + 웹 스택 "WebFlux" 선택 시 — 삭제 (+ Layered면 rename)

이 절은 **언어가 Java인 경우에만** 적용한다. 아래 경로는 모두 `spring/java/` 하위이며, Kotlin은 WebFlux 규칙 자체가 없으므로 위의 "언어 Kotlin + WebFlux 감지" 절을 대신 적용한다.

- 영속성(JPA/SQL-first) 항목을 확정하지 않았으므로 JPA/SQL-first의 삭제 케이스와 MongoDB(JPA용)/QueryDSL 케이스는 적용하지 않는다.
- 선택한 아키텍처의 `.claude/rules/backend/spring/java/{layered|hexagonal}/repository.md`와 `.claude/rules/backend/spring/java/repository-tools.md`, `.claude/rules/backend/spring/java/repository-sql.md`를 삭제한다 (`repository-r2dbc.md`가 대체한다).
- Layered면 `.claude/rules/backend/spring/java/layered/domain.md`(JPA용, orm.xml 전제)를 `git rm`으로 삭제하고, 순수 Domain 변형인 `layered/domain-pure.md`를 `git mv`로 `domain.md`에 맞춰 rename한다. 본문은 편집하지 않는다.
- Hexagonal이면 domain 관련 처리가 없다 (`ports-and-adapters.md`·`hexagonal/domain.md`의 `{Domain}JpaEntity` 언급은 `repository-r2dbc.md` 2번의 대체 규정이 우선한다).
- `test.md`는 편집하지 않는다 — base class 표의 `Jpa*` 접두사는 `webflux.md` 8번의 대체 규정(`R2dbc*` 접두사, WebTestClient)으로 갈음한다.
- (리액티브 MongoDB "R2DBC만 사용" 선택 시) `.claude/rules/backend/spring/java/repository-reactive-mongo.md`와, 선택한 아키텍처의 `.claude/rules/backend/spring/java/{layered|hexagonal}/test-mongodb.md`를 삭제한다. `repository-r2dbc.md` 도입부는 리액티브 MongoDB 참조를 조건부("병용하지 않는 프로젝트는 해당 파일을 제외한다")로 이미 서술하므로 편집하지 않는다.
- (리액티브 MongoDB "함께 사용" 선택 시) `repository-reactive-mongo.md`와 `test-mongodb.md`를 그대로 유지한다.
- `spring-*` 에이전트는 웹 스택(MVC/WebFlux)을 조건부로 함께 언급하므로 수정하지 않는다.

### 백엔드 스택 "Spring Boot" + 영속성 도구 "JPA" 선택 시 — 삭제
- 선택한 언어 디렉토리의 `.claude/rules/backend/spring/{java|kotlin}/repository-sql.md` (SQL-first 전용 규칙)
- (Layered인 경우) `.claude/rules/backend/spring/{java|kotlin}/layered/domain-pure.md` (순수 Domain 변형). JPA용 `layered/domain.md`를 그대로 유지한다.

### 백엔드 스택 "Spring Boot" + 영속성 도구 "SQL-first" 선택 시 — 삭제 (+ Layered면 rename)
- 선택한 언어·아키텍처의 `.claude/rules/backend/spring/{java|kotlin}/{layered|hexagonal}/repository.md`와 `.claude/rules/backend/spring/{java|kotlin}/repository-tools.md`를 삭제한다 (`repository-sql.md`가 대체한다).
- Layered면 `.claude/rules/backend/spring/{java|kotlin}/layered/domain.md`(JPA용, orm.xml 전제)를 `git rm`으로 삭제하고, 순수 Domain 변형인 `layered/domain-pure.md`를 `git mv`로 `domain.md`에 맞춰 rename한다. 본문은 편집하지 않는다.
- Hexagonal이면 domain 관련 처리가 없다 (`ports-and-adapters.md`의 `{Domain}JpaEntity` 언급은 `repository-sql.md` 2번의 대체 규정이 우선한다).
- MongoDB/QueryDSL 항목은 이 경우 확정하지 않았으므로 해당 케이스도 적용하지 않는다.
- `spring-*` 에이전트는 영속성 도구(JPA/SQL-first)를 조건부로 함께 언급하므로 수정하지 않는다.

### 백엔드 포함 + "NestJS" 선택 시 — 삭제
- `.claude/rules/backend/spring/`, `.claude/rules/backend/fastapi/` 전체 (`nestjs/`, `shared/`는 유지)
- `.claude/agents/spring-*.md`(spring-hexagonal-* 포함 14개), `.claude/agents/fastapi-*.md`(7개)

### 백엔드 포함 + "FastAPI" 선택 시 — 삭제
- `.claude/rules/backend/spring/`, `.claude/rules/backend/nestjs/` 전체 (`fastapi/`, `shared/`는 유지)
- `.claude/agents/spring-*.md`(spring-hexagonal-* 포함 14개), `.claude/agents/nestjs-*.md`(7개)

### 프론트엔드 미포함 시 — 삭제
- `.claude/rules/frontend/` 전체
- `.claude/agents/frontend-*.md` (frontend-vue-* 포함 13개 전부)

### 프론트엔드 포함 + Next.js 선택 시 — 삭제
- `.claude/rules/frontend/vite.md`, `.claude/rules/frontend/vue.md`, `.claude/rules/frontend/vite-routing-reactrouter.md`, `.claude/rules/frontend/vite-routing-tanstack.md` (`vite-routing-*`는 Vite 전용 라우팅 규칙이므로 Vite 미선택 시 둘 다 삭제한다. `vite.md`만 삭제하면 이 두 파일이 죽은 규칙으로 남는다)
- Vue.js 전담 에이전트 6개: `.claude/agents/frontend-vue-*.md`

### 프론트엔드 포함 + Vite 선택 시 — 삭제
- `.claude/rules/frontend/nextjs.md`, `.claude/rules/frontend/vue.md`
- Vue.js 전담 에이전트 6개: `.claude/agents/frontend-vue-*.md`

### 프론트엔드 포함 + Vue.js 선택 시 — 삭제
- `.claude/rules/frontend/nextjs.md`, `.claude/rules/frontend/vite.md`, `.claude/rules/frontend/vite-routing-reactrouter.md`, `.claude/rules/frontend/vite-routing-tanstack.md` (`vite-routing-*`는 Vite 전용 라우팅 규칙이므로 Vite 미선택 시 둘 다 삭제한다. `vite.md`만 삭제하면 이 두 파일이 죽은 규칙으로 남는다)
- React 계열 전제 에이전트 6개: `.claude/agents/frontend-architect.md`, `frontend-tdd-implementer.md`, `frontend-refactorer.md`, `frontend-test-author.md`, `frontend-code-reviewer.md`, `frontend-debugger.md` (`frontend-vue-*` 6개와, 프레임워크 무관인 `frontend-style-checker`는 유지)

### 백엔드 스택 "Spring Boot" + "JPA만" 선택 시 — 삭제
- 언어·아키텍처 스타일 답변에 따라 `.claude/rules/backend/spring/{java|kotlin}/{layered|hexagonal}/test-mongodb.md`(MongoDB 추가 테스트 규칙)를 삭제한다. JPA 전용 `test.md`는 그대로 유지한다 (`test.md`는 `test-mongodb.md`를 역참조하지 않으므로 죽은 참조가 남지 않는다).
- ("JPA + MongoDB 함께 사용" 선택 시에는 `test-mongodb.md`를 유지한다.)
- **`spring-*` 에이전트는 수정하지 않는다.** `spring-test-author`/`spring-hexagonal-test-author`의 base class 표에는 MongoDB 행이 남는다. 4단계 보고 시 이 사실을 사용자에게 알린다.

### 백엔드 스택 "Spring Boot" + "Specification까지만" 선택 시 — 삭제
- 선택한 언어 디렉토리의 `.claude/rules/backend/spring/{java|kotlin}/repository-tools.md`(QueryDSL/JdbcClient/jOOQ 상세 규칙)를 삭제한다.
- `repository.md`·`test.md`는 편집하지 않는다 — 이 두 파일은 Level 0~1로 완결돼 있고 `repository-tools.md`를 역참조하지 않으므로(참조 방향은 tools→repository 단방향), 상위 티어 파일만 삭제하면 죽은 참조가 남지 않는다.
- **`spring-*` 에이전트는 수정하지 않는다.** 일부 에이전트(`spring-domain-designer` 등)는 QueryDSL/JdbcClient 티어를 계속 언급한다. 4단계 보고 시 이 사실을 사용자에게 알린다.

### 백엔드 스택 "Spring Boot" + RDB 선택 시 — 보고만 (파일 편집 없음)
- 어떤 DB를 선택하든 rules 파일을 편집하지 않는다. `repository-sql.md`·`repository-tools.md`·`layered/test.md`·(WebFlux) `repository-r2dbc.md`의 jOOQ 방언/R2DBC 드라이버 예시는 PostgreSQL 기준으로 그대로 둔다.
- PostgreSQL 선택 시: 별도 안내가 없다 (예시가 이미 PostgreSQL 기준이다).
- 그 외 DB 선택 시: 4단계 보고에서 "예시는 PostgreSQL 기준이며, 선택한 DB에 맞는 jOOQ 방언 상수(`SQLDialect.MYSQL`/`SQLDialect.MARIADB`/`SQLDialect.SQLITE` 등)와 드라이버로 직접 교체해야 한다"고 안내한다. Oracle·SQL Server처럼 jOOQ 상용 에디션 방언이 필요한 DB면 그 사실도 함께 안내한다.
- `spring-*` 에이전트는 DB 벤더를 언급하지 않으므로 수정 대상이 없다.

### 백엔드 스택 "NestJS" + 영속성 도구 "TypeORM" 선택 시 — 삭제
- `.claude/rules/backend/nestjs/nestjs-persistence-prisma.md`, `.claude/rules/backend/nestjs/nestjs-persistence-sqlfirst.md`를 삭제한다 (`nestjs-persistence-typeorm.md`만 남긴다). 영속성-무관 핵심인 `nestjs.md`는 그대로 유지한다.
- `nestjs-*` 에이전트는 영속성 도구를 조건부로 함께 언급하므로 수정하지 않는다.

### 백엔드 스택 "NestJS" + 영속성 도구 "Prisma" 선택 시 — 삭제
- `.claude/rules/backend/nestjs/nestjs-persistence-typeorm.md`, `.claude/rules/backend/nestjs/nestjs-persistence-sqlfirst.md`를 삭제한다 (`nestjs-persistence-prisma.md`만 남긴다). 영속성-무관 핵심인 `nestjs.md`는 그대로 유지한다.
- `nestjs-*` 에이전트는 영속성 도구를 조건부로 함께 언급하므로 수정하지 않는다.

### 백엔드 스택 "NestJS" + 영속성 도구 "사용 안 함(SQL-first)" 선택 시 — 삭제
- `.claude/rules/backend/nestjs/nestjs-persistence-typeorm.md`, `.claude/rules/backend/nestjs/nestjs-persistence-prisma.md`를 삭제한다 (`nestjs-persistence-sqlfirst.md`만 남긴다). 영속성-무관 핵심인 `nestjs.md`는 그대로 유지한다.
- `nestjs-*` 에이전트는 영속성 도구를 조건부로 함께 언급하므로 수정하지 않는다.

### 백엔드 스택 "FastAPI" + ORM 사용 여부 선택 시 — 삭제
- ORM 사용 선택 시: `.claude/rules/backend/fastapi/fastapi-persistence-sqlfirst.md`를 삭제한다 (`fastapi-persistence-orm.md`만 남긴다). 영속성-무관 핵심인 `fastapi.md`는 그대로 유지한다.
- SQL-first 선택 시: `.claude/rules/backend/fastapi/fastapi-persistence-orm.md`를 삭제한다 (`fastapi-persistence-sqlfirst.md`만 남긴다). 영속성-무관 핵심인 `fastapi.md`는 그대로 유지한다.
- `fastapi-*` 에이전트는 영속성 방식을 조건부로 함께 언급하므로 수정하지 않는다.

### 백엔드 스택 "FastAPI" + RDB 선택 시 — 보고만 (파일 편집 없음)
- 어떤 DB를 선택하든 `fastapi.md`를 편집하지 않는다. 드라이버 예시(3번의 `asyncpg`, 9번의 `psycopg2`/`asyncpg, aiosqlite 등`)는 PostgreSQL 기준으로 그대로 둔다.
- PostgreSQL 선택 시: 별도 안내가 없다 (예시가 이미 PostgreSQL 기준이다).
- 그 외 DB 선택 시: 4단계 보고에서 "예시는 asyncpg(PostgreSQL) 기준이며, 선택한 DB에 맞는 드라이버로 직접 교체해야 한다"고 안내한다 (MySQL/MariaDB: async는 `asyncmy`/`aiomysql`, 동기는 `PyMySQL`; SQLite: async는 `aiosqlite`, 동기는 표준 `sqlite3`).
- `fastapi-*` 에이전트는 DB 벤더를 언급하지 않으므로 수정 대상이 없다.

### 백엔드 스택 "NestJS" + 검증 도구 선택 시 — 삭제
- class-validator 선택 시: `.claude/rules/backend/nestjs/nestjs-validation-zod.md`를 삭제한다 (`nestjs-validation-classvalidator.md`만 남긴다). 검증-무관 핵심인 `nestjs.md`는 그대로 유지한다.
- Zod 선택 시: `.claude/rules/backend/nestjs/nestjs-validation-classvalidator.md`를 삭제한다 (`nestjs-validation-zod.md`만 남긴다). 검증-무관 핵심인 `nestjs.md`는 그대로 유지한다.
- **Zod 선택 시 `nestjs-*` 에이전트는 수정하지 않는다.** `nestjs-tdd-implementer`/`nestjs-code-reviewer`/`nestjs-domain-designer`는 class-validator 전제로 작성돼 있다. 4단계 보고 시 이 사실을 사용자에게 명시적으로 알린다: "nestjs 에이전트들은 class-validator 전제입니다. Zod 기준으로 활용하려면 에이전트를 별도로 조정해야 합니다."

### 풀스택 + 백엔드 "NestJS" 선택 시 — 보고만 (파일 편집 없음)
- 백엔드와 프론트엔드가 모두 TypeScript이므로 `frontend/*.md`의 `paths`(`**/*.ts`, `**/*.tsx`)가 백엔드 소스에도 매칭될 수 있다. rw:init은 이 `paths`를 편집하지 않고 원본 그대로 둔다.
- 대신 4단계 보고에서, 1단계에서 감지했거나 2단계 라운드 3에서 답변받은 프론트엔드 소스 루트 경로를 이용해 "필요하면 `frontend/*.md`의 `paths`를 이 경로로 직접 좁히라(예: `**/*.ts` → `apps/web/**/*.ts`)"고 안내한다.

### 프론트엔드 "Vite" + 라우팅 라이브러리 선택 시 — 삭제
- TanStack Router 선택 시: `.claude/rules/frontend/vite-routing-reactrouter.md`를 삭제한다 (`vite-routing-tanstack.md`만 남긴다). 라우팅-무관 공통인 `vite.md`는 그대로 유지한다.
- React Router 선택 시: `.claude/rules/frontend/vite-routing-tanstack.md`를 삭제한다 (`vite-routing-reactrouter.md`만 남긴다). 라우팅-무관 공통인 `vite.md`는 그대로 유지한다.

### 언어·프레임워크 버전 — 보고만 (파일 편집 없음)

버전은 삭제/rename 대상을 바꾸지 않는다. 어떤 버전을 쓰든 남는 rules 파일 집합은 동일하며, rules 본문의 버전 서술(`repository-tools.md`의 "Spring Boot 3.2+" 등)도 편집하지 않는다. 확정된 버전을 아래 전제와 대조해, **미달하는 항목이 있을 때만** 4단계 보고에서 경고한다. 전제를 모두 충족하면 버전 관련 안내를 출력하지 않는다.

| 전제 | 근거 | 미달 시 보고할 내용 |
|---|---|---|
| JDK 17+ | Java rules 전반이 `record`·`sealed interface`를 Command/Query·Read DTO·Value Object의 기본 표현 수단으로 사용 | 규칙 대부분이 문법적으로 성립하지 않는다. JDK 상향 또는 rules 수정이 필요하다 |
| Spring Boot 3.2+ / Framework 6.1+ | `spring/{java\|kotlin}/repository-tools.md` 2.1(JdbcClient), `repository-sql.md`(모든 영속성 접근을 JdbcClient로 실행) | `JdbcClient`가 없다. SQL-first를 선택했다면 영속성 규칙의 기반 자체가 없으므로 Boot 상향이 사실상 필수이고, JPA + QueryDSL/jOOQ를 선택했다면 Level 3 티어를 쓸 수 없다 |
| Spring Boot 3+ (Jakarta EE) | `repository-tools.md` 1.1(QueryDSL `io.github.openfeign.querydsl` 6.x), Spring rules 전반의 `jakarta.persistence.*` 전제 | Boot 2.x는 `javax.persistence` 시대다. 패키지 전제와 QueryDSL 아티팩트 선택이 모두 어긋나므로 Boot 상향 또는 rules 전반 재검토가 필요하다 |
| Python 3.9+ / Pydantic v2 (FastAPI 0.100+) | `fastapi.md` 2번(Pydantic v2의 인스턴스화 시점 검증이 다중 진입점 방어의 근거). FastAPI가 Pydantic v2를 지원하는 것은 0.100부터다 | Pydantic v1에서는 다중 진입점 방어 논리가 성립하지 않는다. v2 마이그레이션(FastAPI도 함께 상향) 또는 검증 트리거 재설계가 필요하다 |
| Vue 3 | `frontend/vue.md` 1번(Vue 3 + Vite), 2번(Composition API·`<script setup>`) | Vue 2에서는 `vue.md`의 핵심 규칙 대부분이 적용 불가하다 |

- 위 전제 검증은 **해당 규칙 파일이 살아남은 경우에만** 수행한다. 예를 들어 "Specification까지만" 선택으로 `repository-tools.md`를 삭제했고 영속성이 JPA면 `JdbcClient` 전제 경고를 하지 않는다.
- Kotlin·Node 버전은 rules에 명시된 최소 전제가 없다. 확정된 값을 결과 보고의 스택 요약에 기록만 하고 별도 경고를 만들지 않는다.
- 버전 때문에 rules 파일을 추가로 삭제하거나 본문을 편집하지 않는다. 조치 여부는 사용자가 판단한다.

## 4단계: 확인 후 실행

1. 삭제/rename 대상 전체 목록을 사용자에게 보여주고 진행 여부를 확인받는다. 분리 파일을 정규 이름으로 맞추는 rename(예: `domain-pure.md` → `domain.md`)이 있으면 그 항목도 함께 표시한다. 1단계에서 자동 확정한 항목이 있으면 항목별 감지 근거(파일 경로·의존성 이름)를 함께 표시해 잘못 감지된 항목을 사용자가 바로잡을 수 있게 한다. NestJS + Zod를 선택한 경우 "nestjs 에이전트들은 class-validator 전제" 안내를 함께 보여준다.
2. **실행 직전에 상태를 다시 확인한다.** 0단계 확인부터 여기까지는 2단계 질문 응답 대기가 끼어 있어 가장 긴 구간이며, 그 사이 다른 세션이나 사용자가 저장소를 건드렸을 수 있다.
   - `git status --short`를 다시 확인한다. 0단계 시점과 달라졌으면 사용자에게 알리고 계속할지 확인받는다.
   - 대상 목록의 각 경로가 실제로 존재하는지 확인한다. **이미 없는 대상은 오류로 보지 않고 건너뛴다** — 이전 `/rw:init` 실행이나 수동 정리로 먼저 삭제됐을 수 있다. 건너뛴 항목은 목록에 남겨 4단계 보고에서 "이미 없어 건너뜀"으로 표시한다.
3. 확인되면 파일 삭제는 `git rm`으로, 분리 파일의 정규 이름 맞춤(rename)은 `git mv`로 수행한다. rules/agents 파일의 본문은 Edit로 수정하지 않는다.
4. **중간에 실패하면 즉시 멈춘다.** 남은 대상을 계속 밀어붙이지 말고, 이미 적용된 항목과 남은 항목을 나눠 보고한 뒤 사용자 판단을 받는다. 되돌리려면 `git restore --staged --worktree <경로>`(삭제 취소) 또는 `git status`로 확인 후 수동 복구가 가능하다는 점을 함께 안내한다.
5. 자동으로 커밋하지 않는다. 사용자가 요청할 때만 커밋한다.

## 결과 보고

- 선택된 스택 조합 요약 (영역, 백엔드 스택·언어·아키텍처·헥사고날 flavor(Hexagonal+JPA+MVC일 때 Clean/Pragmatic, Java·Kotlin 모두)·주 RDB, 프론트엔드 프레임워크, 확정된 언어·프레임워크 버전(JDK/Kotlin/Spring Boot/Python/FastAPI·Pydantic/Node/Vue) 등)
- 자동 감지 모드였던 경우: 자동 확정한 항목과 항목별 감지 근거, 질문으로 보완한 항목 목록
- 삭제한 파일/디렉토리 목록과 rename한 파일 목록(분리 파일 → 정규 이름). rules 파일 본문은 편집하지 않았음을 함께 명시한다
- 4단계 2항에서 "이미 없어 건너뛴" 대상이 있으면 그 목록 (재실행이거나 수동으로 먼저 정리한 경우)
- 실행 도중 실패해 중단했으면 이미 적용된 항목과 남은 항목을 나눠서 보고하고, 복구 방법을 안내한다
- 해당 시 에이전트 안내문 (NestJS+Zod, JPA만, Specification까지만 선택 시의 에이전트 미수정 안내)
- 해당 시 헥사고날 flavor 안내 (Pragmatic 선택 시: rename한 정규 파일, 제거한 spec-first `api-dto.md`와 `spring-openapi-spec-author` 에이전트(전제가 yaml-first라 code-first 프로젝트에서는 쓰지 않음), `spring-hexagonal-*` 에이전트는 flavor를 규칙에서 읽어 동작하므로 삭제하지 않았음. Pragmatic+MongoDB면 `test-mongodb.md` 참조 방식 안내)
- 해당 시 Kotlin + WebFlux 미지원 경고 (2단계에서 "MVC 기준으로 계속 진행"을 선택한 경우: 남은 규칙이 블로킹 스택 전제라 리액티브 코드에 그대로 적용할 수 없고, Kotlin용 WebFlux/R2DBC 규칙은 직접 작성해야 함)
- 해당 시 ArchUnit 안내 (Java·Kotlin 모두 `spring/{java|kotlin}/archunit.md`가 Layered/Hexagonal 양쪽에 유지되어 아키텍처 테스트 강제 대상임)
- 해당 시 버전 전제 경고 (확정된 버전이 3단계 "언어·프레임워크 버전 — 보고만"의 전제에 미달하는 항목이 있을 때만. 전제를 모두 충족하면 이 항목은 출력하지 않는다). 버전 때문에 파일을 삭제하거나 편집하지 않았음을 함께 명시한다
- 해당 시 RDB 안내문 (PostgreSQL 외 DB 선택 시: 예시가 PostgreSQL/asyncpg 기준이므로 jOOQ 방언 상수·드라이버를 직접 교체하라는 안내. Oracle·SQL Server처럼 jOOQ 상용 에디션 방언이 필요한 경우 그 사실도 함께)
- 해당 시 풀스택 NestJS `paths` 안내문 (frontend `paths`가 백엔드 TS에도 매칭될 수 있으니 필요하면 프론트엔드 소스 루트로 직접 좁히라는 안내)
- 커밋하지 않았음을 명시하고, 커밋을 원하면 요청하도록 안내

## 하지 말아야 할 것

- 사용자 확인 없이 바로 삭제/rename을 실행하지 않는다.
- 0단계의 워킹트리 확인 결과만 믿고 실행 직전 재확인을 건너뛰지 않는다(4단계 2항). 질문 응답을 기다리는 동안 저장소가 바뀔 수 있다.
- 이미 없는 대상 때문에 전체 실행을 실패로 끝내지 않는다. 건너뛰고 보고한다(4단계 2항).
- 원본 템플릿 저장소에서 실행해 원본을 훼손하지 않는다(0단계 참조).
- 확정되지 않은 조합을 임의로 판단해서 처리하지 않는다.
- 이 템플릿이 지원하지 않는 조합(Kotlin + WebFlux)을 감지하고도 조용히 다른 값(MVC)으로 덮어써서 진행하지 않는다. 사용자에게 알리고 확인받는다(1단계·2단계 라운드 3 참조).
- 근거가 약하거나 상충하는 항목을 추측으로 자동 확정하지 않는다. 미확정으로 남겨 2단계에서 질문한다.
- rules 파일을 삭제/rename했더라도 전제가 다른 에이전트(`nestjs-*`의 class-validator 전제, `spring-*`의 MongoDB/QueryDSL 언급 등)를 임의로 고치지 않는다(별도 작업). 4단계 보고에서 안내만 한다.
