# Gateway 관찰성(로깅/트레이싱) 규칙 (Servlet MVC)

게이트웨이의 요청/응답 로깅, 민감정보 마스킹, 로그 저장소 규칙을 정의한다.

> 적용 범위: 이 규칙은 게이트웨이 코드 전용이다. 백엔드 서비스용 규칙은 [[rules-readme]]를 참조한다.

## 1. 로그 저장소

- 로그 저장소는 **console(기본) 또는 file**만 지원한다. `LoggingProperties.StorageType`은 `NONE | FILE`이다.
- 게이트웨이 트래픽 로그를 **관계형 DB(JPA/JDBC)에 저장하지 않는다**. 트래픽 로그는 고처리량 경로이며 RDB는 병목/단일 장애점이 된다. 운영 규모 로깅은 stdout → 외부 수집기(ELK/Loki 등)로 보낸다.
- `CompositeLogStorage`가 단일 진입점(`@Primary`)이다: console에 항상 출력하고, `storage=FILE`일 때 `FileLogStorage`에 추가 기록한다. 개별 저장소는 자체적으로 console에 중복 출력하지 않는다.
- 가상 스레드 위에서 동작하므로 file/console 블로킹 I/O를 그대로 사용한다.

## 2. 민감정보 마스킹

- **헤더 마스킹**: `SensitiveHeaders.mask(...)`로 요청·응답 양쪽 로그에 일관 적용한다. 마스킹 대상 집합: `Authorization`, `Cookie`, `Set-Cookie`, `X-API-Key`, `X-User-Id`, `X-User-Roles`. 헤더는 유지하되 값을 `***`로 가린다.
- **바디 마스킹**: `SensitiveFieldMasker`로 JSON 바디의 민감 필드(`password`, `token` 등)를 마스킹한다.
- 마스킹 대상 집합을 확장할 때는 위 두 유틸의 상수만 수정한다.

## 3. 바디 캐싱

- 요청 바디 캐싱은 servlet `ContentCachingRequestWrapper`, 응답 바디 캡처는 `ContentCachingResponseWrapper`를 사용한다.
- **캐시 한도(cacheLimit)와 로그 표시 한도(`maxBodySize`)를 분리**한다. cacheLimit은 메모리 상한(충분히 크게)이고, 로그 truncate는 `maxBodySize` 기준으로 별도 적용한다. 둘을 같은 값으로 두면 truncate 표시가 누락된다.
- `multipart/form-data` 요청은 바디 캐싱·로깅에서 제외한다.
- `ContentCachingResponseWrapper` 사용 후 반드시 `copyBodyToResponse()`를 호출한다.

## 4. 로깅 타이밍

- 요청 로깅: 바디가 불필요하면(`include-body=false`) **`chain.doFilter` 전**에 기록한다(응답 타이밍 race 없음). 바디가 필요하면 라우팅 후 캐시된 바디로 기록한다.
- 응답 로깅: 응답 wrapper를 가장 바깥에서 씌우고 라우팅 후 기록한다.

## 5. 트레이싱 헤더

- `X-Request-Id`는 인입 헤더가 있으면 유지, 없으면 생성하여 다운스트림 요청과 클라이언트 응답 양쪽에 전파한다.
- `X-B3-TraceId`/`X-B3-SpanId`는 생성하여 다운스트림으로 전달한다.

## 6. 금지 패턴

- 트래픽 로그의 RDB 저장.
- 민감 헤더/바디를 마스킹 없이 로깅.
- cacheLimit을 `maxBodySize`와 동일하게 설정(truncate 누락).
- `ContentCachingResponseWrapper` 사용 후 `copyBodyToResponse()` 누락(빈 응답).
