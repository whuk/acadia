# 서비스별 다운스트림 docs 경로 설정 Plan (발견 3: Swagger 집계)

게이트웨이 swagger 집계가 springdoc 규약(`/v3/api-docs`)을 하드코딩해, FastAPI 등 `/openapi.json`을 쓰는
비-springdoc 백엔드의 문서를 가져오지 못한다(swagger-ui 드롭다운엔 뜨지만 선택 시 404).

수정: `ServiceConfig`에 **`docsPath`(기본 `/v3/api-docs`)**를 추가하고, swagger 프록시 라우트가
`setPath(service.docsPath)`로 백엔드의 실제 문서 경로를 가리키게 한다. 기본값은 기존 동작을 유지한다.

> 게이트웨이 swagger-ui 페이지와 swagger-config(URL 목록)는 그대로 동작한다. 이번 변경은 **다운스트림 문서 프록시 경로**만 설정 가능하게 한다.

## 1. docsPath 설정으로 다운스트림 문서 라우팅

- [x] shouldRouteServiceDocsToConfiguredDocsPath — `docs-path=/openapi.json` 서비스의 `/v3/api-docs/{service}` 요청이 백엔드 `/openapi.json`으로 라우팅됨 (`SwaggerDocsPathTest`, RED→GREEN)
- [x] shouldDefaultDocsPathToSpringdoc — 기본값 `/v3/api-docs` 유지, 기존 `SwaggerUiTest` 전부 통과

## 2. 실제 백엔드 검증

- [x] e2eFastApiSwagger — uvicorn 백엔드(`docs-path=/openapi.json`)로 재가동: `/v3/api-docs/axe` → 200, FastAPI OpenAPI(3.1.0, 6 paths) 반환. swagger-ui 200. 집계 성공
- [x] sideFix — #77이 노출한 `ConnectionTimeoutTest` 플래키(connect 실패 502/504 비결정)를 502 OR 504 허용 + 타이밍 검증으로 안정화(별도 커밋)

## 작업 규율 (CLAUDE.md)
- API 수준 실패 테스트 먼저 → 구현 → 회귀 + 실제 검증. 전체 테스트·ktlint 통과. 사용자 요청 전 자동 커밋 금지.
