# PRD.md — API Gateway 구축을 위한 제품 요구사항 문서

## 1. 문서 목적
본 문서는 React 기반 프론트엔드와 다수의 Spring Boot 백엔드 서비스 간의 통신 진입점을 통합하기 위한 API Gateway를 설계하기 위한 요구사항을 정의한다. API Gateway는 인증, 라우팅, 로깅, 트래픽 제어 등 플랫폼 수준의 공통 기능을 수행하며, 전체 시스템의 확장성·성능·보안·운영 편의성을 확보하는 것을 목표로 한다.

## 2. 문맥 / 배경 (Context & Background)
현재 프론트엔드는 React로 구성되어 있으며, 백엔드는 여러 Spring Boot 인스턴스 또는 마이크로서비스로 확장될 예정이다. 프론트는 여러 백엔드 엔드포인트를 직접 호출하게 되면 인증 분산, 로깅/추적 어려움, 장애 분석 문제, 일관성 문제 등이 발생한다. 이를 해결하기 위해 API Gateway를 구축한다.

## 3. 제품 목표 (Goals)
1. 프론트엔드 API 진입점을 /api/** 로 단일화  
2. 서비스별 라우팅 기능 제공  
3. JWT 기반 인증 및 인가 검증  
4. 요청/응답 로깅 및 TraceId 기반 추적  
5. Circuit Breaker, Retry, Timeout 적용  
6. Rate Limiting(optional)  
7. 공통 보안/운영 기능 중앙화  
8. 비동기 고성능 처리(WebFlux)

## 4. Non-Goals
- 비즈니스 로직 처리  
- 데이터 집계(Aggregation)  
- GraphQL 스키마 통합 또는 BFF 기능  
- 프론트 특화 응답 제공  
- 무거운 Transform/Mapping  

## 5. 대상 사용자 (Stakeholders)
프론트엔드 개발자, 백엔드 개발자, DevOps/SRE, QA, 보안팀.

## 6. 기술 스택
- **프로젝트명**: Acadia
- **기본 패키지**: me.ryan.acadia
- Spring Boot 4.x, Java 21, Kotlin 2.2
- Spring Cloud Gateway 2025.x
- WebFlux(Netty)
- Spring Security 7 + OAuth2 Resource Server(JWT)
- Prometheus, OpenTelemetry
- Resilience4j
- Redis Rate Limiter(optional)

## 7. 기능 요구사항 (Functional Requirements)

### 7.1 라우팅
예:  
- /api/users/** → user-service  
- /api/orders/** → order-service  

### 7.2 인증
- Authorization: Bearer JWT  
- 유효성 검증 실패 시 401  
- 사용자 정보는 X-User-Id, X-User-Roles 로 전달  

### 7.3 인가
게이트웨이에서는 기본 인가만 수행.

### 7.4 로깅 & 트레이싱
- JSON Log  
- X-Request-Id 생성  
- TraceId/SpanId 전달  

### 7.5 Rate Limiting(optional)
- 초당 10 요청, 버스트 20  
- Redis 기반 토큰 버킷

### 7.6 Circuit Breaker
- failure rate 50% → open  
- retry 3회  

### 7.7 Timeout
- 전체 요청 3초  
- backend 연결 1초  

### 7.8 CORS
- allow origins: https://example.com  
- allow methods: GET, POST, PUT, DELETE  
- allow credentials: true  

## 8. 비기능 요구사항
- p95 응답시간 50ms 이하  
- 2000 TPS 처리  
- 무상태 운영
- Java 21
- HTTPS mandatory  

## 9. 아키텍처 다이어그램

React SPA  
 → API Gateway  
   → user-service  
   → order-service  
   → auth-service  
   → file-service  

## 10. 기본 규칙

### 10.1 URL
모든 외부 API는 /api/** 로 통일

### 10.2 헤더
- X-Request-Id  
- X-User-Id  
- X-User-Roles  

### 10.3 실패 응답
- 인증 실패: 401  
- 인가 실패: 403  
- backend 오류: 502  
- timeout: 504

## 11. 향후 확장
- BFF(GraphQL) 레이어  
- 모바일 전용 API  
- API 키 발급  
- SLA 기반 리밋  
- API usage 대시보드

## 12. 구현 범위 (MVP)
- 기본 Gateway 구성  
- JWT 인증  
- 라우팅  
- Circuit breaker  
- Prometheus  
- Docker/K8s 배포  

## 13. 성공 기준
- 모든 API 호출이 /api/** 로 통일  
- 인증/라우팅 정상 동작  
- 장애시 p95 < 200ms  
- 호출량/지연/실패율 모니터링 가능  
