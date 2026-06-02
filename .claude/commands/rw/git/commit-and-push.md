---
argument-hint: "새 브랜치명 (선택). 입력 시 해당 이름으로 브랜치를 만들고 거기에 commit/push 합니다."
---

현재 작업 디렉토리의 변경사항을 의미 있는 단위로 commit 하고 remote 에 push 합니다. PR 생성·머지는 수행하지 않습니다. 보호 브랜치(`main`, `master`, `develop`, `dev`)에서 직접 작업 중인 경우 새 브랜치로 분기할지 사용자에게 확인합니다.

## 1단계: 변경사항 수집 및 사전 검증

1. 작업 디렉토리 상태를 확인하세요:
   - `git status --short` — 변경/신규/삭제 파일 목록
   - `git diff --stat HEAD` — 스테이징 + 비스테이징 변경 요약
   - 변경 의도가 모호하면 `git diff HEAD` 또는 Read 로 내용을 확인하세요.
2. 변경사항이 전혀 없으면(클린 워킹 트리) 다음 메시지를 출력하고 **중단**하세요:
   > 작업 디렉토리에 변경사항이 없습니다. commit/push 할 내용이 없으므로 종료합니다.
3. `.env`, `*.key`, `*.pem`, `credentials.*`, `secrets.*` 등 민감 파일이 변경 대상에 포함되어 있으면 사용자에게 경고하고 명시적 확인을 받기 전까지 staging 에서 제외하세요.

## 2단계: 대상 브랜치 결정

다음 순서로 분기 판정하세요.

### 경로 A: `$ARGUMENTS`에 브랜치명이 입력된 경우

1. 입력값에서 양쪽 공백을 제거합니다. 한국어·공백·특수문자가 포함되면 다음 메시지를 출력하고 **중단**하세요:
   > 브랜치명은 영어 소문자·숫자·하이픈(`-`)·슬래시(`/`)만 사용할 수 있습니다.
2. 동일 이름 브랜치가 이미 존재하는지 확인하세요(`git rev-parse --verify <브랜치명>`).
   - 존재하면 숫자 접미사(`-2`, `-3`, …)를 붙여 충돌이 없는 이름을 찾습니다. 변경된 이름을 사용자에게 알려줍니다.
3. `git checkout -b <브랜치명>` 으로 새 브랜치를 생성한 뒤 4단계로 진행하세요.

### 경로 B: `$ARGUMENTS`가 비어 있고 현재 브랜치가 보호 브랜치(`main`, `master`, `develop`, `dev`)인 경우

1. AskUserQuestion 으로 사용자에게 묻습니다:
   - 옵션 1 (권장): 새 브랜치를 생성해 거기에 commit/push 한다.
   - 옵션 2: 현재 보호 브랜치에 그대로 commit/push 한다 (위험).
2. 옵션 1을 선택하면, 변경 내용 요약을 바탕으로 `/rw:git:branch` 와 동일한 규칙으로 브랜치 유형(`feature/`, `fix/`, `refactor/`, `test/`, `chore/`)과 영어 소문자 + 하이픈 브랜치명을 생성한 뒤 `git checkout -b <브랜치명>` 으로 새 브랜치를 만듭니다.
3. 옵션 2를 선택하면 4단계로 진행하되, 4단계 사용자 확인에서 "보호 브랜치 직접 push" 경고를 명시적으로 노출합니다.

### 경로 C: 일반 브랜치에서 작업 중인 경우

추가 동작 없이 4단계로 진행합니다.

## 3단계: 커밋 메시지 작성

1. `git diff --cached`(스테이징된 변경)와 `git diff`(비스테이징 변경)를 종합해 변경의 **성격**을 식별하세요:
   - 새 기능 (`add`, `implement`) / 기존 기능 보강 (`update`, `improve`) / 결함 수정 (`fix`) / 리팩터링 (`refactor`) / 테스트 (`test`) / 설정·문서 (`chore`, `docs`)
2. 커밋 메시지 형식:
   - 첫 줄: 50자 내외, 동사 원형으로 시작, "왜"를 우선해 작성
   - 본문(필요 시): 한 줄 비우고 80자 폭으로 줄바꿈. 변경 동기와 영향을 1~3줄로 설명
   - 마지막에 `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>` trailer 포함
3. 변경 범위가 크고 논리적으로 분리되는 경우 사용자에게 알리고 다중 커밋으로 분할할지 확인하세요. 기본은 단일 커밋입니다.

## 4단계: Staging + Commit 수행

1. 사용자에게 다음을 보여주고 확인을 받으세요:
   - 대상 브랜치명 (신규 생성된 경우 그 사실 포함)
   - 스테이징할 파일 목록
   - 작성한 커밋 메시지 전문
   - 경로 B-옵션 2로 진입했다면 "보호 브랜치 직접 push" 경고
2. 확인되면 파일을 명시적으로 `git add <files...>` 합니다. `git add -A` / `git add .` 는 사용하지 마세요.
3. 커밋 메시지는 HEREDOC 으로 전달합니다:
   ```bash
   git commit -m "$(cat <<'EOF'
   <subject>

   <body 옵션>

   Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
   EOF
   )"
   ```
4. 커밋 실패 시(예: pre-commit 훅 위반) 원인을 진단하고 수정한 뒤 **새 커밋**을 만드세요. `--amend` 는 사용하지 마세요. `--no-verify` 는 사용자가 명시적으로 요청한 경우에만 사용하세요.

## 5단계: Push 수행

1. 현재 브랜치의 upstream 설정 여부를 확인하세요(`git rev-parse --abbrev-ref --symbolic-full-name @{u}` 가 실패하면 upstream 없음).
   - upstream 이 없으면 `git push -u origin <브랜치명>` 으로 push + 추적 설정을 동시에 수행합니다.
   - upstream 이 있으면 `git push` 만 수행합니다.
2. push 가 거부된 경우(non-fast-forward 등) 강제 push 를 자동으로 시도하지 마세요. 원인을 사용자에게 보고하고 처리 방법을 확인받으세요.
3. 보호 브랜치(`main`, `master`, `develop`, `dev`)에 push 하는 경우 절대 `--force` / `--force-with-lease` 를 사용하지 마세요. 사용자가 명시적으로 요청해도 위험을 한 번 더 경고하세요.

## 6단계: 결과 보고

다음을 간결하게 보고하세요:

- 사용된 경로 (A: argument 브랜치 / B-1: 새 분기 / B-2: 보호 브랜치 직접 / C: 일반 브랜치)
- 최종 브랜치명과 upstream
- 생성된 커밋 SHA(짧은 해시)와 subject
- push 결과(범위 갱신 또는 새 브랜치 생성 여부)

## 하지 말아야 할 것

- PR 생성·머지 수행하기 (이 커맨드의 범위가 아님 — 필요 시 `/commit-commands:commit-push-pr` 또는 `/rw:git:squash-merge-pull` 사용 안내)
- 사용자 확인 없이 커밋·push 수행하기
- `git add -A` / `git add .` 로 무차별 staging
- `.env` 등 민감 파일을 경고 없이 commit 하기
- 보호 브랜치에 `--force` / `--force-with-lease` 사용하기
- 커밋 실패 시 `--amend` 로 이전 커밋 덮어쓰기
- `--no-verify` / `--no-gpg-sign` 등 안전장치 우회 (사용자 명시 요청 시 한정)
- 변경사항이 없는데 빈 커밋 만들기
- 현재 브랜치에 commit 후 다른 브랜치로 checkout 하여 push 하기 (요청 흐름 외 부수 작업 금지)

$ARGUMENTS - 선택 사항: 새로 만들 브랜치명 (영어 소문자·숫자·하이픈·슬래시). 입력 시 해당 이름으로 분기 후 commit/push 합니다.
