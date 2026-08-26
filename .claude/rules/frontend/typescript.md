---
description: TypeScript 프론트엔드 공통 컨벤션 (Next.js, Vite, Vue 공통 적용)
paths:
  - "**/*.ts"
  - "**/*.tsx"
  - "**/*.vue"
---

# TypeScript 프론트엔드 공통 규칙

Next.js, Vite, Vue 등 프레임워크와 무관하게 TypeScript 프론트엔드 코드 전반에 적용되는 공통 규칙이다. 프레임워크 고유 규칙은 `nextjs.md`, `vite.md`, `vue.md`를 참조한다.

## 1. TypeScript 컴파일러 설정

- `tsconfig.json`에 `strict: true`를 기본으로 켠다. 추가로 `noUncheckedIndexedAccess`를 켜서 배열/객체 인덱스 접근 시 `undefined` 가능성을 타입에 반영한다.
- 타입만 가져올 때는 `import type`을 사용한다.
- `any`를 사용하지 않는다. 불가피하면 `unknown` + 타입 좁히기로 대체한다.

## 2. 타입 작성 컨벤션

- `enum` 대신 `as const` 객체 + union type을 우선한다. 트리쉐이킹, 구조적 타이핑과의 호환성이 더 좋다.
- 설정 객체처럼 리터럴 타입 추론이 필요한 곳에는 `satisfies`를 사용해 타입 좁힘과 리터럴 추론을 동시에 확보한다.
- TypeScript 타입은 컴파일타임에만 존재한다. API 응답, 폼 입력 등 **외부 경계를 넘는 데이터**는 Zod 등으로 런타임 검증한다 (백엔드의 Bean Validation과 같은 이유: 타입 선언만으로는 실제 값을 보장하지 못한다).

## 3. 폴더 구조와 의존 방향 (feature-based)

- 기능 단위(`features/{feature}/`)로 코드를 묶는다. 각 feature 폴더 내부에 `api/`, `components/`, `hooks/`(Vue는 `composables/`), `stores/`, `types/` 등 필요한 하위 구조를 둔다.
- 의존 방향은 **`shared → features → app`** 한 방향이다. `shared`는 `features`를 import 할 수 없고, **feature끼리도 서로 직접 import 하지 않는다.** 공유가 필요해지면 해당 로직을 `shared`로 끌어올린다.
- 이 원칙은 백엔드 `shared/architecture.md` 3번(계층 역참조 금지, 스킵 레벨 허용)과 동일한 사상이다.
- 라우팅 구조와 feature 폴더를 어떻게 매핑할지는 프레임워크마다 다르므로 `nextjs.md`/`vite.md`/`vue.md`를 따른다.

## 4. 상태관리: 서버 상태와 클라이언트 상태 분리

- **서버에서 가져온 데이터**(API 응답 등)는 TanStack Query로 관리한다. 캐싱, 재검증, mutation, optimistic update를 자체 상태 저장소에 직접 구현하지 않는다.
- **서버에서 오지 않는 순수 UI 상태**(모달 열림 여부, 사이드바, 테마 등)만 클라이언트 상태 라이브러리(React는 Zustand 또는 범위가 좁으면 `useState`/Context, Vue는 Pinia — `vue.md` 5번 참조)로 관리한다.
- 서버 데이터를 클라이언트 상태 스토어에 복사해 넣지 않는다. 두 계층을 분리하면 실제로 필요한 전역 클라이언트 상태는 매우 작아진다.
- Redux는 신규 프로젝트의 기본값이 아니다. 기존에 Redux로 구축된 프로젝트를 유지보수하는 경우에만 예외로 허용한다.

## 5. 스타일링

- Tailwind CSS를 기본으로 한다. 컴포넌트 단위 스타일링이 필요하면 CSS Modules를 예외적으로 허용한다.
- 재사용 UI 컴포넌트는 npm 패키지로 설치하지 않고 shadcn/ui 방식(Radix 기반 프리미티브 + Tailwind 스타일을 프로젝트 코드로 복사)을 우선 검토한다. Vue는 이에 대응하는 shadcn-vue를 사용한다(`vue.md` 6번 참조). 팀은 prop/variant API 설계에 집중한다.
- 컴포넌트 variant는 `class-variance-authority`(CVA)로 선언한다. 컴포넌트가 새로 렌더링/인스턴스화될 때마다 재생성되지 않도록 모듈 스코프에서 정의한다.
- 조건부 클래스 및 Tailwind 클래스 충돌 병합은 `clsx` + `tailwind-merge`를 합친 `cn()` 유틸로 처리한다.

## 6. 테스트

- Unit/Integration 테스트는 Vitest + 프레임워크에 맞는 Testing Library(React는 React Testing Library, Vue는 `@testing-library/vue` — `vue.md` 7번 참조)를 사용하고, 테스트 대상 소스와 **같은 디렉토리에 colocate**한다 (`Button.tsx` ↔ `Button.test.tsx`).
- 테스트는 구현 세부사항이 아니라 **사용자가 사용하는 방식**(접근성 role, label, 텍스트)으로 쿼리한다. `data-testid`는 다른 방법이 마땅치 않을 때만 사용한다.
- E2E 테스트는 Playwright를 사용하고, 개별 feature가 아닌 **최상위 `e2e/` 디렉토리**에 둔다 (여러 feature를 가로지르는 시나리오를 검증하므로).
- 테스트 커버리지 수치를 목표로 강제하지 않는다. 크리티컬 경로와 엣지 케이스를 우선한다.
- 반복 사용되는 테스트 데이터(props, API 응답 mock 등)는 인라인 객체 리터럴을 복사하지 않고 Fishery 팩토리(`Factory.define`)로 정의한다. 고유값이 필요한 필드는 `sequence`로 만든다.
- 랜덤 값이 필요한 필드는 `@faker-js/faker`를 병용하되 테스트 셋업에서 `faker.seed(...)`를 고정해 실패를 재현할 수 있게 한다. 어서션 대상 필드는 `factory.build({...})` 오버라이드로 명시적으로 고정한다 — 어서션이 랜덤 값에 의존하면 안 된다.
- 경계값(최소/최대, 경계 ±1, 빈 값, 임계점)은 랜덤으로 뽑지 않고 명시적으로 고정한다. 같은 로직을 여러 입력으로 검증할 때는 `test.each`/`it.each`로 케이스를 나열하고, 검증 규칙(길이, 범위, 패턴)이 있는 입력은 유효 경계와 무효 경계 양쪽 케이스를 모두 포함한다.

## 7. 린트/포맷

- 신규 프로젝트는 Biome을 기본으로 한다(포맷+린트 통합, 별도 Prettier 불필요).
- 이미 ESLint(flat config) + Prettier로 구축된 프로젝트를 유지보수하는 경우 그대로 유지해도 된다. 마이그레이션을 강제하지 않는다.

## 8. 패키지 매니저

- pnpm을 기본으로 한다. 여러 앱/패키지를 함께 다루는 모노레포 구조라면 Turborepo를 함께 검토한다.

## 9. 금지 패턴

- 컴포넌트/훅 파일에서 `any` 타입을 사용하지 않는다.
- feature 폴더끼리 서로 직접 import 하지 않는다.
- 서버에서 가져온 데이터를 Zustand/Pinia 등 클라이언트 상태 스토어에 그대로 복사해 넣지 않는다.
- 컴포넌트 정의 내부에서 CVA variant 객체를 매번 새로 생성하지 않는다.
- 테스트에서 `data-testid`를 role/label보다 우선 사용하지 않는다.
