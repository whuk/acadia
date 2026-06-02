# B3 Trace Propagation Plan (B1)

`gateway-observability.md §5` 개정에 따라 B3 트레이싱 헤더 처리를 수정한다.
TraceId는 보존(연속성), SpanId는 hop별 생성 + 부모 연결, 인입 헤더는 형식 검증 후 신뢰한다.

> 진행: "go" 시 체크되지 않은 첫 테스트를 구현하고, 그 테스트만 통과시키는 최소 코드를 작성한다.
> 통합 테스트는 `@SpringBootTest`(RANDOM_PORT) + WireMock으로 백엔드 수신 헤더를 검증한다.

## 규칙 개정 (완료)

- [x] `gateway-observability.md §5` 개정: TraceId 보존, SpanId 부모 연결, 형식 검증. §6 금지 패턴 보강

## 1. 형식 검증 헬퍼

- [x] shouldValidateB3TraceIdAndSpanId — `B3Ids.isValidTraceId`(16/32 hex), `isValidSpanId`(16 hex), 소문자 hex만 (`B3IdsTest`)

## 2. TraceId 보존/생성

- [x] shouldPreserveValidInboundTraceId — 유효 인입 `X-B3-TraceId` 그대로 전달 (`B3TraceIdPropagationTest`). 원복 시 RED 확인
- [x] shouldRegenerateInvalidInboundTraceId — 부적합 인입은 hex 새 값으로 대체

> 인입 없음 → 생성은 기존 `TraceIdPropagationTest`(전달·형식)가 커버한다.

## 3. SpanId 생성 + 부모 연결

- [x] shouldAlwaysGenerateNewSpanId — 인입 SpanId가 있어도 새 SpanId 전달(`B3SpanIdPropagationTest`)
- [x] shouldSetParentSpanIdFromValidInboundSpanId — 유효 인입 SpanId → `X-B3-ParentSpanId`
- [x] shouldStripParentSpanIdWhenNoValidInboundSpan — 유효 인입 SpanId 없으면 클라이언트 주입 ParentSpanId 제거(strip-then-set)

## 작업 규율 (CLAUDE.md)
- 구조적/동작 변경 분리, 매 단계 전체 테스트 실행, 사용자 요청 전 자동 커밋 금지.
