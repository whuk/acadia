---
description: Next.js App Router 고유 컨벤션
paths:
  - "**/app/**"
  - "**/next.config.*"
  - "**/middleware.ts"
---

# Next.js (App Router) 규칙

`typescript.md`의 공통 규칙을 전제로, Next.js App Router 고유 규칙만 다룬다.

이 프로젝트가 실제로 Vite+React나 Vue.js를 채택했다면 이 파일은 적용 대상이 아니다. Next.js, Vite, Vue.js를 한 프로젝트에서 동시에 쓰지 않으므로, 실제로 채택하지 않은 프레임워크의 규칙 파일은 프로젝트에서 제외한다 (`vite.md`, `vue.md` 참조).

## 1. 라우팅과 폴더 구조

- `app/` 디렉토리는 파일시스템 라우팅이다. 해당 세그먼트에 `page.tsx` 또는 `route.ts`가 있어야만 실제 라우트로 노출되므로, 그 전까지는 컴포넌트, 훅, 유틸을 라우트 폴더에 자유롭게 **콜로케이션**해도 안전하다.
- URL에 영향을 주지 않고 라우트를 그룹핑하려면 `(group)` 형태의 Route Group을 사용한다.
- 라우팅 대상에서 완전히 제외할 폴더는 `_folder`(언더스코어 프리픽스)로 표시한다.
- `typescript.md` 3번의 feature 폴더는 각 라우트 세그먼트 내부에 콜로케이션하거나, 여러 라우트에서 공유되면 `app/` 바깥의 `features/`로 끌어올린다.

## 2. Server Component / Client Component 경계

- Server Component가 기본값이다. 이벤트 핸들러, `useState`/`useEffect`, `localStorage`/`window` 등 브라우저 API, 커스텀 훅이 필요할 때만 `"use client"`를 선언한다.
- 페이지 전체를 클라이언트 컴포넌트로 만들지 않는다. 인터랙션이 필요한 **리프(leaf) 노드까지 경계를 최대한 내려서** 클라이언트 번들 크기를 최소화한다.
- 서버에서 페칭한 데이터는 props로 클라이언트 컴포넌트에 전달한다. 클라이언트 컴포넌트 내부에서 다시 페칭하지 않는다.

## 3. 데이터 페칭

- Server Component에서는 별도 Route Handler를 거치지 않고 **데이터 소스에서 직접 fetch**한다. Route Handler를 한 번 더 경유하면 왕복 지연이 생기고 prerender가 실패할 수 있다.
- Route Handler/클라이언트 페칭(TanStack Query)은 `geolocation`, `localStorage`처럼 클라이언트 전용 API에 의존하거나 폴링이 필요한 경우로 한정한다.
- 독립적인 다중 페칭은 `Promise.all()`로 병렬화한다. 같은 요청이 여러 컴포넌트에서 중복되면 React `cache()`로 감싼다.
- Next.js는 기본적으로 fetch 결과를 캐싱한다. 매번 최신 데이터가 필요한 요청은 `cache: 'no-store'` 또는 `next: { revalidate: 0 }`를 명시적으로 지정한다. 이 옵션을 누락하는 것이 실무에서 가장 흔한 실수다.

## 4. 환경변수, 메타데이터, 최적화

- 브라우저에 노출돼야 하는 환경변수만 `NEXT_PUBLIC_` 프리픽스를 붙인다. 그 외 값은 서버 전용으로 유지한다.
- 페이지 메타데이터는 `<head>`를 직접 조작하지 않고 Metadata API(`generateMetadata`, `metadata` export)를 사용한다.
- 이미지는 `next/image`, 폰트는 `next/font`를 사용한다. `<img>` 태그나 외부 폰트 `<link>`를 직접 쓰지 않는다.

## 5. 금지 패턴

- 필요 이상으로 넓은 범위에 `"use client"`를 선언하지 않는다 (레이아웃/페이지 최상단에 습관적으로 붙이지 않는다).
- Server Component에서 자기 자신의 Route Handler를 fetch로 호출하지 않는다.
- 캐시 무효화가 필요한 요청에서 캐시 옵션 지정을 누락하지 않는다.
