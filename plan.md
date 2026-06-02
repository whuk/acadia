# FileLogStorage 효율 개선 Plan (B2)

`FileLogStorage`가 `store()` 호출마다 `mkdirs()` + `FileWriter` open/append/close 하는 비효율을 제거한다.
규칙(`gateway-observability.md §1`)은 가상 스레드 위 블로킹 file I/O를 허용하므로 비동기로 바꾸지 않고,
**단일 BufferedWriter 재사용 + mkdirs 1회 + 동시성 안전 + 종료 시 close + 라인당 flush**로 개선한다.

> 성격: 동작 보존 성능 **리팩터링**. RED-드라이버보다 특성화/가드 테스트로 동작 보존을 검증한다(CLAUDE.md: 리팩터링 전후 테스트 통과 확인).
> 기존 `CompositeLogStorageTest`는 `FileLogStorage`를 mock하므로 영향 없음. FileLogStorage 직접 검증 테스트는 신규 작성.

## 1. 특성화/가드 테스트 (현재 구현에서 green 확인 후 리팩터링)

- [x] writesEntriesAsJsonLinesCreatingParentDirs — 부모 디렉토리 생성 + JSON 라인 추가 (`FileLogStorageTest`, baseline green)
- [x] concurrentWritesPreserveAllLines — 16스레드×50라인 동시 store 시 800라인 모두 정상 JSON (공유 writer 동시성 가드)

## 2. 리팩터링 (테스트 green 유지)

- [x] reuseBufferedWriterAndMkdirsOnce — 단일 BufferedWriter lazy 오픈·재사용, mkdirs 1회, `store()` 동기화 + 라인당 flush, `DisposableBean.destroy()`로 종료 시 close. 특성화 테스트 green 유지
- [x] closeReleasesFileHandle — `destroy()` 후 재기록 시 핸들 재오픈되어 append 이어짐

## 작업 규율 (CLAUDE.md)
- 리팩터링 전후 전체 테스트 통과 확인. 사용자 요청 전 자동 커밋 금지.
