---
argument-hint: "squash merge할 PR 번호 (예: #123 또는 123, 선택)"
---

GitHub PR을 squash merge하고 로컬 main 브랜치를 최신 상태로 동기화합니다.

1. 대상 PR을 결정하세요:
   - `$ARGUMENTS`가 PR 번호(예: `#123`, `123`)이면 해당 PR을 대상으로 합니다
   - `$ARGUMENTS`가 없으면 현재 저장소의 가장 최근 열린 PR을 대상으로 합니다
2. 대상 PR의 상태를 확인하세요:
   - `gh pr view <PR번호> --json title,state,mergeable,headRefName,baseRefName`으로 PR 정보를 조회합니다
   - PR이 이미 머지되었거나 닫혀 있으면 사용자에게 알리고 중단합니다
   - merge 가능 여부(mergeable)를 확인하고, 충돌이 있으면 사용자에게 알리고 중단합니다
3. 머지 전 사용자에게 확인을 요청하세요:
   - PR 제목, 번호, 소스 브랜치 → 타겟 브랜치 정보를 표시합니다
4. squash merge를 수행하세요:
   - `gh pr merge <PR번호> --squash --delete-branch`를 실행합니다
5. 로컬 main 브랜치를 최신 상태로 동기화하세요:
   - `git checkout main && git pull origin main`을 실행합니다
6. 정리 작업을 수행하세요:
   - 삭제된 리모트 브랜치에 대응하는 로컬 브랜치가 있으면 `git branch -d`로 정리합니다
   - `git fetch --prune`으로 리모트 추적 브랜치를 정리합니다
7. 최종 결과를 보고하세요:
   - 머지된 PR 정보 (번호, 제목)
   - 현재 브랜치와 최신 커밋 정보

하지 말아야 할 것:
- 사용자 확인 없이 머지를 실행하기
- merge 불가능한 PR을 강제로 머지하기
- main 브랜치가 아닌 다른 브랜치에서 pull 수행하기
- 로컬에 커밋되지 않은 변경사항이 있을 때 checkout하기 (stash 또는 중단 안내)

$ARGUMENTS - 선택 사항: squash merge할 PR 번호 (예: #123 또는 123)
