# Status Line Configuration Prompt

아래 프롬프트를 Claude Code 세션 시작 시 복사해서 사용하세요.
새 프로젝트나 다른 머신으로 이동했을 때 이 파일을 참고하여 상태표시줄을 설정합니다.

---

## 📋 프롬프트 (복사하여 일괄 붙여넣기)

```text
현재 세션에서 매번 응답의 마지막(또는 시작)에 아래 형식의 상태표시줄(status line)을 출력해줘.

**표시 형식:**
📁 {현재 디렉토리} | 🤖 {모델명} | 🌿 {git branch}

**구현 요구사항:**
1. **OS 환경 맞춤형 동작 (필수)**: 클라이언트(사용자)의 OS 환경(Windows/macOS/Linux)이 매번 다를 수 있으므로, 현재 내 환경의 OS를 먼저 식별한 후 그 환경에 맞는 셸 명령어를 사용하여 정보를 가져올 것.
2. **현재 작업 디렉토리**:
   - Windows (PowerShell): `(Get-Location).Path`
   - macOS/Linux (Bash/Zsh): `pwd`
3. **모델명**: 현재 대화 중인 당신의 모델명을 표시.
4. **git 브랜치**: 다음 명령어를 실행하여 가져올 것.
   - Windows: `git rev-parse --abbrev-ref HEAD 2>$null`
   - macOS/Linux: `git rev-parse --abbrev-ref HEAD 2>/dev/null`
   - git 저장소가 아닌 경우 `(no git)` 문자열 표시.

**출력 예시:**
- Windows 환경:
  📁 C:\Users\ryan-woo\dev\my-project | 🤖 claude-3-7-sonnet | 🌿 feature/add-login
- macOS/Linux 환경:
  📁 /home/ryan/dev/my-project | 🤖 claude-3-7-sonnet | 🌿 main
```

---

## 📝 참고사항

- 이 파일은 새 프로젝트 시작 또는 머신 간 이동 시 위 프롬프트를 Claude Code에 붙여넣어 터미널과 동일한 느낌의 상태표시줄 테마를 유지하기 위한 템플릿입니다.
- **토큰 사용량 확인 방침**: 상태표시줄에 표시할 경우 AI의 환각(부정확한 임의 숫자 출력)이 발생하므로 제외되었습니다. 토큰 사용량 및 비용 확인이 필요할 때는 Claude Code 내장 명령어인 `/cost`를 입력하여 정확한 공식 데이터를 확인하세요.
- 터미널(OS) 환경이 다를 수 있으므로, 프롬프트 최상단에 OS를 자체 식별하여 실행하도록 지침이 포함되어 있습니다.
