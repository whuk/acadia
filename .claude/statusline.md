# Status Line 설정 가이드

이 저장소가 제공하는 `.claude/statusline-command.sh`로 Claude Code 상태표시줄을 설정하는 방법입니다.
새 프로젝트나 다른 머신으로 이동했을 때 이 파일을 참고하여 동일한 상태표시줄을 재현합니다.

## 출력 형태

Oh My Zsh `robbyrussell` 테마의 프롬프트를 재현하고, 뒤에 컨텍스트 사용률과 모델명을 덧붙입니다.

```text
➜  my-project git:(main) ✗ · 🧠 ████░░░░  47% · 🤖 Opus 5
```

git 저장소가 아니면 git 부분이, stdin JSON에 해당 필드가 없으면 🧠/🤖 부분이 통째로 생략됩니다.

## 동작 방식

Claude Code는 `settings.json`의 `statusLine` 필드에 등록된 셸 커맨드를 실행하고, **세션 데이터를 JSON으로 stdin에 넘긴 뒤 그 출력을 입력창 아래 전용 행에 렌더링**합니다. 로컬에서 실행되므로 API 토큰을 소모하지 않고, 컨텍스트 사용률 같은 수치를 추정이 아닌 실제 값으로 받아 씁니다.

스크립트가 읽는 것은 stdin JSON과 git 메타데이터뿐이며 네트워크 요청을 하지 않습니다.

## 요구사항

- Claude Code v1.0.80 이상
- `jq` — stdin JSON 파싱에 사용합니다 (`brew install jq`)
- `git` — git 세그먼트에만 필요하며, 없으면 해당 부분만 생략됩니다

Nerd Font는 필요하지 않습니다. 다만 게이지 문자(`█` `░`)와 이모지(🧠 🤖)가 렌더링되는 터미널 폰트가 필요합니다.

## 설치

`statusLine`은 프로젝트 설정과 홈 설정 어느 쪽에도 등록할 수 있습니다. 프로젝트 설정이 홈 설정보다 우선합니다.

### 이 프로젝트에서만 사용

`.claude/settings.json`에 추가합니다.

```json
{
  "statusLine": {
    "type": "command",
    "command": "bash \"$CLAUDE_PROJECT_DIR/.claude/statusline-command.sh\""
  }
}
```

상태표시줄 커맨드는 프로젝트 루트에서 실행되므로 `bash .claude/statusline-command.sh`처럼 상대경로로 써도 동작합니다. `$CLAUDE_PROJECT_DIR`를 쓰는 편이 세션 중 작업 디렉토리가 바뀌어도 안전합니다.

### 모든 프로젝트에서 사용

스크립트를 홈으로 복사한 뒤 `~/.claude/settings.json`에 절대경로로 등록합니다.

```bash
cp .claude/statusline-command.sh ~/.claude/statusline-command.sh
```

```json
{
  "statusLine": {
    "type": "command",
    "command": "bash /Users/<사용자명>/.claude/statusline-command.sh"
  }
}
```

이 저장소는 템플릿이므로 `.claude/settings.json`에 `statusLine`을 미리 넣어두지 않았습니다. 템플릿을 복사한 모든 프로젝트에 상태표시줄이 강제되는 것을 피하기 위함이며, 필요한 쪽에서 위 설정을 직접 추가합니다.

## 표시 항목

| 세그먼트 | 내용 | 색 |
|---|---|---|
| `➜` | 프롬프트 화살표 | 초록 |
| 디렉토리 | 현재 디렉토리 basename | 청록 |
| `git:(브랜치)` | 브랜치명. detached HEAD면 short SHA | 괄호 파랑, 브랜치 빨강 |
| `✗` / `✔` | working tree dirty / clean | 노랑 / 초록 |
| 🧠 게이지 + % | 컨텍스트 사용률 (8칸 게이지) | 0~59% 초록, 60~84% 노랑, 85%+ 빨강 |
| 🤖 모델명 | 현재 모델. `(1M context)` 같은 괄호 부분은 제거 | dim |

게이지는 1% 이상이면 최소 1칸을 채우고, 정확히 100%일 때만 8칸을 모두 채웁니다. 게이지와 숫자 사이 간격은 고정이라 값이 바뀌어도 폭이 흔들리지 않습니다.

## 커스터마이징

스크립트를 직접 편집합니다.

| 대상 | 위치 |
|---|---|
| 색 구간 임계값(60/85) | `color_for_pct()` |
| 게이지 칸 수·문자 | `filled`/`gauge`를 계산하는 `awk` 블록 |
| 이모지 | `ctx_segment`/`model_segment`의 `printf` |
| 세그먼트 순서 | 마지막 `printf`의 인자 순서 |

이모지에는 색 코드를 적용하지 않습니다. 컬러 이모지에는 ANSI 색이 먹지 않고, 이모지 뒤에 리셋이 걸리면 오히려 색이 끊깁니다.

## 참고사항

- 상태표시줄은 로컬에서 실행되며 **API 토큰을 소모하지 않습니다.** 세션 시작 시 1회, 이후 새 어시스턴트 메시지 도착·`/compact` 완료·권한 모드 변경·vim 모드 전환 시 자동으로 갱신됩니다(300ms 디바운스).
- 컨텍스트 사용률이 상시 표시되므로 `/context`를 따로 실행할 필요가 줄어듭니다. 비용은 이 스크립트에 넣지 않았으며, `cost.total_cost_usd`는 클라이언트 추정값이라 정확한 수치는 `/cost`로 확인합니다.
- stdin JSON에는 이 스크립트가 쓰지 않는 필드도 많습니다 — `rate_limits`(5시간/7일 소진율), `cost`, `effort`, `output_style`, `transcript_path` 등. 필드 전체 목록은 [공식 문서](https://code.claude.com/docs/en/statusline)를 참고하세요.
- 직접 만드는 대신 완성된 플러그인을 쓰려면 [claude-hud](https://github.com/jarrodwatts/claude-hud)가 있습니다. 실행 중인 툴·서브에이전트·todo 진행 상황까지 transcript를 파싱해 보여줍니다.
