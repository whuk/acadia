# API Gateway 구현 계획서 (TDD)

## 프로젝트 정보
- **프로젝트명**: Acadia
- **기본 패키지**: me.ryan.acadia
- **Java 버전**: 21
- **Kotlin 버전**: 2.2
- **Spring Boot**: 4.x
- **Spring Cloud Gateway**: 2025.x

## 개요
Spring Cloud Gateway 기반 API Gateway MVP 구현을 위한 TDD 테스트 계획

## Phase 1: 프로젝트 설정 및 기본 구조
- [x] 1.1 Spring Boot 애플리케이션이 정상 기동된다
- [x] 1.2 Actuator health endpoint가 200을 반환한다
- [x] 1.3 WebFlux 기반으로 동작한다 (Netty 서버)
- [x] 1.4 Kotlin lint(ktlint)가 설정되어 코드 스타일을 검사한다

## Phase 2: 기본 라우팅
- [x] 2.1 /api/users/** 요청이 user-service로 라우팅된다
- [x] 2.2 /api/orders/** 요청이 order-service로 라우팅된다
- [x] 2.3 정의되지 않은 경로는 404를 반환한다
- [x] 2.4 라우팅 시 원본 HTTP 메서드가 유지된다
- [x] 2.5 라우팅 시 원본 헤더가 전달된다
- [x] 2.6 라우팅 시 원본 바디가 전달된다

## Phase 3: 인증 (JWT)
- [x] 3.1 Authorization 헤더 없는 요청은 401을 반환한다
- [x] 3.2 잘못된 JWT 토큰은 401을 반환한다
- [x] 3.3 만료된 JWT 토큰은 401을 반환한다
- [x] 3.4 유효한 JWT 토큰은 라우팅이 진행된다
- [x] 3.5 JWT에서 추출한 사용자 ID가 X-User-Id 헤더로 전달된다
- [x] 3.6 JWT에서 추출한 역할이 X-User-Roles 헤더로 전달된다
- [x] 3.7 공개 경로(/api/public/**)는 인증 없이 접근 가능하다

## Phase 4: 장애 대응 (Resilience)
- [x] 4.1 백엔드 응답이 3초 초과 시 504를 반환한다
- [x] 4.2 백엔드 연결이 1초 초과 시 504를 반환한다
- [x] 4.3 백엔드 실패 시 최대 3회 재시도한다
- [x] 4.4 실패율 50% 초과 시 Circuit Breaker가 열린다
- [x] 4.5 Circuit Breaker 열린 상태에서 503을 반환한다
- [x] 4.6 백엔드 5xx 오류는 502로 변환된다

## Phase 5: 로깅 & 트레이싱
- [x] 5.1 모든 요청에 X-Request-Id가 생성된다
- [x] 5.2 클라이언트가 보낸 X-Request-Id가 있으면 유지된다
- [x] 5.3 요청 로그가 JSON 형식으로 기록된다
- [x] 5.4 응답 로그가 JSON 형식으로 기록된다
- [x] 5.5 TraceId가 백엔드로 전달된다
- [x] 5.6 SpanId가 백엔드로 전달된다

## Phase 6: CORS & 보안
- [x] 6.1 허용된 Origin에서 CORS preflight 요청이 성공한다
- [x] 6.2 허용되지 않은 Origin은 CORS 오류를 반환한다
- [x] 6.3 허용된 HTTP 메서드만 CORS 응답에 포함된다
- [x] 6.4 credentials가 허용된다

## Phase 7: Prometheus 메트릭
- [x] 7.1 /actuator/prometheus 엔드포인트가 메트릭을 반환한다
- [x] 7.2 요청 수 메트릭이 기록된다
- [x] 7.3 응답 시간 메트릭이 기록된다
- [x] 7.4 Circuit Breaker 상태 메트릭이 기록된다

## Phase 8: Rate Limiting (Optional)
- [x] 8.1 초당 10 요청 초과 시 429를 반환한다
- [x] 8.2 버스트 20 요청까지 허용된다
- [x] 8.3 Rate Limit 헤더가 응답에 포함된다
- [x] 8.4 Rate Limit 리셋 시간이 헤더에 포함된다
- [x] 8.5 Rate Limiting이 비활성화되면 제한 없이 요청이 통과한다
- [x] 8.6 Rate Limiting이 비활성화되면 Rate Limit 헤더가 응답에 포함되지 않는다

## Phase 9: Swagger Aggregation
- [x] 9.1 /swagger-ui.html 엔드포인트가 Swagger UI를 반환한다
- [x] 9.2 /v3/api-docs 엔드포인트가 Gateway의 OpenAPI 스펙을 반환한다
- [x] 9.3 등록된 서비스의 api-docs가 Gateway를 통해 프록시된다 (/v3/api-docs/{service-name})
- [x] 9.4 Swagger UI 드롭다운에서 각 서비스를 선택할 수 있다
- [x] 9.5 서비스 설정에서 swagger-enabled: false인 서비스는 목록에서 제외된다
- [x] 9.6 swagger-ui.urls가 gateway.services 기반으로 동적 생성된다

## Phase 10: Swagger 그룹 통합
- [x] 10.1 SwaggerGroupFetcher가 서비스의 swagger-config에서 그룹 목록을 가져온다
- [ ] 10.2 서비스 연결 실패 시 빈 목록을 반환한다
- [ ] 10.3 동적으로 가져온 그룹이 Swagger URL에 서비스/그룹 형식으로 추가된다
- [ ] 10.4 동적 페칭 실패 시 정적 swaggerGroups 설정을 폴백으로 사용한다
- [ ] 10.5 그룹이 없는 서비스는 기존처럼 서비스 단위로 URL이 생성된다
- [ ] 10.6 /v3/api-docs/{service}/{group} 요청이 백엔드의 /v3/api-docs/{group}으로 라우팅된다
- [ ] 10.7 Swagger UI swagger-config에서 서비스/그룹 형식의 URL 목록이 반환된다

---

## 진행 상태
- 총 테스트: 56개
- 완료: 49개
- 진행률: 88%

## 사용법
```
/go          # 다음 미완료 테스트 구현
/test        # 전체 테스트 실행
/tidy        # 리팩토링
/commit      # 커밋 생성
```
