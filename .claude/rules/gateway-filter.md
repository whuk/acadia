# Gateway 필터 및 라우팅 규칙 (Servlet MVC)

이 프로젝트는 **Spring Cloud Gateway Server WebMVC + Java 21 가상 스레드**(servlet/Tomcat) 스택이다. 게이트웨이의 필터와 라우팅을 작성·수정할 때 다음 규칙을 따른다.

> 적용 범위: `domain.md`, `repository.md`, `service-layer.md`, `api-dto.md`, `rest-api.md`, `layer-communication-rules.md`는 백엔드 서비스 모듈용 규칙이며 이 게이트웨이 코드에는 적용하지 않는다. ([[rules-readme]] 참조)

## 1. 필터 모델

- 횡단 관심사(인증, 요청ID/트레이스, rate limit, 로깅)는 **`OncePerRequestFilter`**로 작성한다. reactive `GlobalFilter`/`WebFilter`를 사용하지 않는다.
- 라우트 단위 변환(stripPrefix, rewritePath, circuitBreaker, retry 등)은 라우트 빌더의 `.before()`/`.after()`/`.filter()`로 적용한다.
- 필터는 모든 요청에 적용된다는 점을 전제한다. reactive `GlobalFilter`는 매칭된 라우트에만 적용됐지만 servlet 필터는 actuator/swagger/preflight 등 게이트웨이 자체 엔드포인트에도 적용된다. 적용 대상을 제한하려면 `shouldNotFilter(...)`를 사용한다.

## 2. 필터 순서

- 모든 필터 순서는 `filter/FilterOrders` 객체의 상수로 **중앙 관리**한다. 각 필터에 매직 넘버 `@Order`를 직접 쓰지 않는다.
- 낮은 값이 먼저(바깥) 실행된다. 기본 순서: RateLimit → RequestId → Trace → Span → ResponseLogging → CachedBody → RequestLogging → BackendError → JWT.
- 순서를 바꿀 때는 의존 관계(예: RequestId가 로깅보다 먼저 부여되어야 함)를 근거로 `FilterOrders`만 수정한다.

## 3. 다운스트림 헤더 주입

- `HttpServletRequest`는 불변이다. 다운스트림으로 헤더를 추가/덮어쓰기/제거하려면 **`common/HeaderInjectingRequestWrapper`**로 감싸 `chain.doFilter(wrapped, response)`에 전달한다.
- 신뢰 헤더는 반드시 "제거 후 설정(strip-then-set)" 한다. 상세는 [[gateway-security]].

## 4. 라우팅 (RouterFunction)

- 라우트는 `RouterFunction<ServerResponse>` 빈으로 정의한다. `GatewayRouterFunctions.route(id)` + `HandlerFunctions.http()` + `BeforeFilterFunctions.uri(url)` 조합을 사용한다.
- 모든 HTTP 메서드를 받으려면 `RequestPredicates.path(pattern)` predicate를 사용한다. 다운스트림 URI는 `http(String)`(deprecated)이 아니라 `.before(uri(...))`로 설정한다.
- 여러 라우트는 각각 `build()`한 뒤 `RouterFunction.and()`로 결합한다.

## 5. Resilience

- Circuit Breaker: `CircuitBreakerFilterFunctions.circuitBreaker { it.setId(...).setStatusCodes(...) }`. 인스턴스 설정은 `application.yml`의 `resilience4j.circuitbreaker.instances.*`에 둔다.
- Retry: `RetryFilterFunctions.retry { it.setRetries(...).setSeries(...).setMethods(...) }`. `spring-retry` 의존성이 있어야 견고한 retry 구현(`FrameworkRetry`가 아닌)이 선택된다.
- 타임아웃: `spring.cloud.gateway.server.webmvc.httpclient.connect-timeout`/`read-timeout`으로 설정하며, 초과 시 504가 반환된다.

## 6. 에러 응답 통일

- 게이트웨이 발생 예외는 `@RestControllerAdvice`(`GatewayExceptionHandler`)에서 통일된 형식으로 매핑한다.
  - `CallNotPermittedException`(circuit open) → 503
  - `CircuitBreakerStatusCodeException` → 502
- 백엔드 5xx → 502 변환은 **응답 status 레벨**에서 처리한다. Gateway MVC는 프록시 응답을 servlet response에 직접 쓰므로 `.after()`의 `ServerResponse`/`setStatus` 변환이 반영되지 않는다. `HttpServletResponseWrapper`로 `setStatus`/`sendError`를 가로채는 servlet 필터(`BackendErrorFilter`)를 사용한다. 게이트웨이 자체 상태(502/503/504)는 통과시킨다.
- 에러 응답 본문은 문자열 보간이 아니라 `ObjectMapper`/`ProblemDetail`로 직렬화한다.

## 7. 블로킹

- 가상 스레드 위에서 동작하므로 **블로킹 I/O(JDBC, 파일, 동기 HTTP)를 그대로 사용**한다. `boundedElastic` 오프로드나 `Mono`/`Flux` 래핑이 필요 없다.
- 외부 호출은 `RestClient`(동기)를 사용한다. `WebClient`/`.block()`을 사용하지 않는다.

## 8. 금지 패턴

- reactive 타입(`Mono`/`Flux`/`ServerWebExchange`/`DataBuffer`) 사용.
- 필터에서 `.subscribe()`로 응답을 쓰고 별도 값을 반환하는 패턴(응답 누락 위험).
- `FilterOrders`를 거치지 않은 개별 `@Order` 매직 넘버.
- 프록시 응답 status를 `.after()`/`ServerResponse`로 바꾸려는 시도(반영되지 않음).
