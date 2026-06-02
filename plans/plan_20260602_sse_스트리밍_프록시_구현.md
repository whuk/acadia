# SSE 스트리밍 프록시 Plan (발견 2)

게이트웨이가 백엔드의 SSE(`text/event-stream`) 스트리밍 응답을 프록시하지 못하고 500을 반환한다.
원인: 프록시 클라이언트(JDK HttpClient)의 read-timeout이 **전체 응답** 타임아웃이라, 장시간 스트림을
read-timeout(3s)에 취소(`subscription cancelled`)한다.

수정: 프록시 클라이언트를 **Apache HttpComponents**로 전환한다. Apache의 read-timeout은 **읽기 간 유휴
소켓 타임아웃(SO_TIMEOUT)**이라, 데이터가 계속 흐르는 스트림은 살아남고 진짜 멈춘 백엔드만 타임아웃된다.
부수 효과로 h2c 업그레이드도 없고(HTTP/1.1) 응답 헤더 케이스도 보존된다.

> #77에서 Apache를 connect-timeout 502/504 비결정성 때문에 제외했으나, 그 테스트는 이미 502/504 허용으로
> 안정화됨(#78). Apache 전환으로 POST(h2c)·SSE·헤더케이스가 모두 해결된다.

## 1. 스트리밍 패스스루

- [x] shouldStreamLongLivedResponseWithoutTimeout — 20청크/4s(간격 ~200ms < 2s read-timeout) 스트림이 끝까지 전달됨 (`StreamingPassthroughTest`, WireMock dribble). JDK 임시 원복 시 RED(2s에 취소) 확인
- [x] shouldStillTimeOutOnStalledBackend — 데이터 없는 hung 백엔드는 read-timeout에 504 (기존 `ResponseTimeoutTest` 통과)

## 2. 회귀 + 실제 검증

- [x] regressionPostAndConnect — 전체 119 테스트 통과(POST/연결/헤더로깅 포함), ktlint 통과
- [x] e2eSse — uvicorn 백엔드 기본 read-timeout 3s: SSE 메시지 200(123 data 라인). 종합 e2e: GET 200, POST 201, SSE 200, Swagger 200
- [x] implementation — 프록시 클라이언트를 Apache HttpComponents로 전환. read-timeout이 per-read 유휴(SO_TIMEOUT)라 스트림 유지. 실측: 1s read-timeout으로도 3.7s SSE 완료 확인. h2c·헤더케이스도 동시 해결

## 작업 규율 (CLAUDE.md)
- API 수준 실패 테스트 먼저 → 구현 → 회귀 + 실제 검증. 전체 테스트·ktlint 통과. 사용자 요청 전 자동 커밋 금지.
