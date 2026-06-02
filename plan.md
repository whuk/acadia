# 프록시 HTTP/1.1 고정 Plan (다운스트림 POST 버그)

게이트웨이 프록시의 HTTP 클라이언트(JDK HttpClient 기본 HTTP/2)가 다운스트림 요청에 h2c 업그레이드 헤더
(`Upgrade: h2c`, `HTTP2-Settings`, `Connection: Upgrade, HTTP2-Settings`)를 붙여, uvicorn/h11 같은 엄격한
HTTP/1.1 백엔드가 **바디 있는 요청(POST/PUT/PATCH)**을 "Invalid HTTP request"로 거부한다(400).

수정: 컨텍스트에 **HTTP/1.1로 빌드한 `ClientHttpRequestFactory` 빈**을 제공한다. Gateway MVC의
`gatewayRestClientCustomizer`가 `ObjectProvider<ClientHttpRequestFactory>.ifAvailable { builder.requestFactory(it) }`로
프록시 RestClient에 적용한다. 기존 connect(1s)/read(3s) 타임아웃은 팩토리에 명시해 유지한다.

> 재현: WireMock(Jetty)은 h2c를 관대히 수용해 재현 불가. **raw TCP 서버로 게이트웨이가 보낸 원시 요청 헤더를
> 캡처**하여 업그레이드 헤더 부재를 검증한다.

## 1. 다운스트림 요청에서 h2c 업그레이드 제거

- [x] proxyFactoryDoesNotSendH2cUpgrade — `GatewayHttpClientConfig.proxyClientHttpRequestFactory`로 POST 시 원시 요청에 `Upgrade`/`HTTP2-Settings` 헤더가 없다(raw 소켓 캡처, `GatewayHttpClientConfigTest`). 함께 기본 JDK 클라이언트(HTTP/2)가 `HTTP2-Settings`를 보냄을 재현해 버그 메커니즘 문서화. (※ 통합 테스트는 테스트 클래스패스의 reactor-netty가 프록시 클라이언트를 가려 재현 불가 → 단위 테스트로 구동)

> 구현: JDK HttpClient를 `HTTP_1_1`로 고정한 `JdkClientHttpRequestFactory` 빈 제공. Gateway MVC `gatewayRestClientCustomizer`가 프록시 RestClient에 적용. connect(1s)/read(3s) 타임아웃 유지.
> Apache HttpComponents도 검토했으나 비라우팅 IP connect 실패가 502/504로 비결정적이라 JDK로 결정. JDK는 응답 헤더명을 소문자화(HTTP/2 표준, 안전)하므로 `ResponseLoggingTest` 헤더 단언을 case-insensitive로 완화.

## 2. 회귀 확인 (기존 테스트 유지)

- [x] regressionBodyForwardingAndTimeouts — `BodyForwardingTest`(POST 바디 전달), `ConnectionTimeoutTest`(504~1s), `ResponseTimeoutTest`(504@3s) 모두 통과. 전체 116 테스트 통과
- [x] e2eVerifiedAgainstUvicorn — 실제 백엔드(uvicorn 8080)로 재가동: 수정 전 POST 400("Invalid HTTP request") → **수정 후 201**(대화 생성). PUT 바디도 정상 전달(백엔드 검증 400)

## 작업 규율 (CLAUDE.md)
- API 수준 실패 테스트 먼저 → 구현 → 회귀 확인. 전체 테스트·ktlint 통과. 사용자 요청 전 자동 커밋 금지.
