# Phase 1: 프로젝트 설정 및 기본 구조

## 목표
Spring Cloud Gateway 기반 프로젝트 초기 설정 및 기본 동작 확인

## 프로젝트 정보
- **프로젝트명**: Acadia
- **기본 패키지**: me.ryan.acadia

## 기술 스택
- Java 21
- Kotlin 2.2
- Spring Boot 4.x
- Spring Cloud Gateway 2025.x
- WebFlux (Netty)
- Gradle Kotlin DSL

## 테스트 목록

### 1.1 Spring Boot 애플리케이션이 정상 기동된다
```kotlin
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ApplicationStartupTest {
    @Test
    fun `애플리케이션이 정상적으로 기동된다`() {
        // 컨텍스트 로드 성공 확인
    }
}
```

### 1.2 Actuator health endpoint가 200을 반환한다
```kotlin
@Test
fun `health 엔드포인트가 UP 상태를 반환한다`() {
    webTestClient.get().uri("/actuator/health")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.status").isEqualTo("UP")
}
```

### 1.3 WebFlux 기반으로 동작한다 (Netty 서버)
```kotlin
@Test
fun `Netty 서버로 동작한다`() {
    // ReactiveWebServerFactory가 NettyReactiveWebServerFactory인지 확인
}
```

## 구현 가이드

### build.gradle.kts
```kotlin
plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "me.ryan"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.0")
    }
}
```

### application.yml
```yaml
server:
  port: 8080

spring:
  application:
    name: acadia

management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus
  endpoint:
    health:
      show-details: always
```

## 완료 조건
- [ ] 모든 테스트 통과
- [ ] 애플리케이션 기동 시간 < 5초
- [ ] Actuator 엔드포인트 접근 가능
