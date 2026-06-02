# Gateway 보안 규칙 (Servlet MVC)

API 게이트웨이의 인증·신뢰 헤더·시크릿 처리 규칙을 정의한다. Spring Cloud Gateway MVC(servlet) 기준이다.

> 적용 범위: 이 규칙은 게이트웨이 코드 전용이다. 백엔드 서비스용 규칙은 [[rules-readme]]를 참조한다.

## 1. 인증 적용 범위

- JWT 인증은 **게이트웨이가 프록시하는 보호 경로(`/api/**`)에만** 적용한다. `OncePerRequestFilter.shouldNotFilter(...)`로 다음을 제외한다:
  - 게이트웨이 자체 엔드포인트: `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`
  - CORS preflight: `OPTIONS` 메서드
- 공개 경로(`/api/public/**`)는 인증을 생략하되, 신뢰 헤더 strip은 수행한다(2절).
- 인증되지 않은 보호 경로 요청은 경로 존재 여부와 무관하게 **401**을 반환한다(미정의 경로라도 인증 우선). 인증된 사용자의 미정의 경로는 404다.

## 2. 신뢰 헤더 strip-then-set

- `X-User-Id`, `X-User-Roles` 등 게이트웨이가 주입하는 신뢰 헤더는 **항상 클라이언트 입력을 무력화**한 뒤 검증된 JWT에서 파생한 값으로만 설정한다.
- `HeaderInjectingRequestWrapper`로 처리한다:
  - `X-User-Id`: 항상 JWT subject로 override.
  - `X-User-Roles`: roles 클레임이 있으면 set, 없으면 **제거**한다(빈 값으로 두지 않는다). roles가 없는 토큰으로 클라이언트가 `X-User-Roles`를 주입하는 권한 상승을 차단한다.
- 공개 경로에서도 인입 신뢰 헤더를 제거한다.

## 3. 경로 화이트리스트 경계

- 인증 면제 경로를 `startsWith`로 매칭할 때는 **경계를 고정**한다. 접두사 뒤에 `/`를 포함하거나 정확 일치를 사용해, `/api/publicX`, `/v3/api-docs-evil` 같은 우회를 차단한다.
- 공개 접두사 상수는 trailing slash를 포함한다(예: `/api/public/`).

## 4. JWT 시크릿

- 시크릿은 **외부 주입**한다(`JWT_SECRET` 환경변수). 소스/기본값에 하드코딩하지 않는다.
- `JwtProperties`는 생성 시 검증한다: 시크릿 길이 **256비트(32바이트) 이상**. 미설정/약한 시크릿이면 기동에 실패(fail-fast)한다.
- prod 프로파일은 `jwt.secret: ${JWT_SECRET}`로 환경변수를 필수화한다. dev/test는 명시적 기본값을 허용한다.
- HMAC 검증은 `Jwts.parser().verifyWith(secretKey)`로 수행한다. 알고리즘을 토큰 헤더에서 신뢰하지 않는다.

## 5. CORS

- `CorsFilter`(servlet) + `UrlBasedCorsConfigurationSource`로 구성한다.
- prod `allowed-origins`는 환경변수로 주입하며 개발용 기본값(`example.com` 등)이 운영에 적용되지 않게 한다.
- `allow-credentials: true`와 와일드카드 origin을 함께 쓰지 않는다.

## 6. 금지 패턴

- 신뢰 헤더(`X-User-*`)를 인입값 제거 없이 다운스트림으로 전달.
- 인증 면제 경로를 경계 없는 `startsWith`로 매칭.
- JWT 시크릿 하드코딩 또는 미검증.
- 에러 응답 본문에 사용자 입력을 문자열 보간(JSON injection). `ObjectMapper`로 직렬화한다.
