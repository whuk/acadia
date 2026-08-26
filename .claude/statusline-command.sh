#!/bin/sh
# Oh My Zsh "robbyrussell" theme look-alike status line.
input=$(cat)

# Current working directory (basename only, like robbyrussell)
cwd=$(echo "$input" | jq -r '.workspace.current_dir // .cwd // empty')
dir_name=$(basename "$cwd")

GREEN='\033[32m'
CYAN='\033[36m'
BLUE='\033[34m'
RED='\033[31m'
YELLOW='\033[33m'
DIM='\033[90m'
RESET='\033[0m'

# 0-59/60-84/85+ color band used by the ctx segment
color_for_pct() {
  p=$1
  if [ "$p" -ge 85 ]; then
    printf '%s' "$RED"
  elif [ "$p" -ge 60 ]; then
    printf '%s' "$YELLOW"
  else
    printf '%s' "$GREEN"
  fi
}

git_segment=""
if git -C "$cwd" --no-optional-locks rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  branch=$(git -C "$cwd" --no-optional-locks branch --show-current 2>/dev/null)
  if [ -z "$branch" ]; then
    branch=$(git -C "$cwd" --no-optional-locks rev-parse --short HEAD 2>/dev/null)
  fi

  if [ -n "$(git -C "$cwd" --no-optional-locks status --porcelain 2>/dev/null)" ]; then
    status_symbol=$(printf "${YELLOW}✗${RESET}")
  else
    status_symbol=$(printf "${GREEN}✔${RESET}")
  fi

  git_segment=$(printf " ${BLUE}git:(${RED}%s${BLUE})${RESET} %s" "$branch" "$status_symbol")
fi

# Context window usage (segment omitted if the field is absent, e.g. older CLI versions)
ctx_segment=""
used_pct=$(echo "$input" | jq -r '.context_window.used_percentage // empty')
if [ -n "$used_pct" ]; then
  pct_int=$(awk -v p="$used_pct" 'BEGIN{printf "%.0f", p}')
  ctx_color=$(color_for_pct "$pct_int")

  # 8-cell gauge: round to nearest cell, but keep >=1% visible as >=1 filled
  # cell, and reserve a full 8/8 gauge for exactly 100%.
  filled=$(awk -v p="$pct_int" 'BEGIN{
    f = int(p / 100 * 8 + 0.5);
    if (p >= 1 && f < 1) f = 1;
    if (p < 100 && f > 7) f = 7;
    if (p == 0) f = 0;
    print f;
  }')
  gauge=$(awk -v f="$filled" 'BEGIN{
    full = ""; empty = "";
    for (i = 0; i < f; i++) full = full "█";
    for (i = 0; i < 8 - f; i++) empty = empty "░";
    print full empty;
  }')

  # Fixed space between the gauge and the %3d value keeps the width stable
  # even at 100%, where %3d alone has no padding left to separate them.
  # The 🧠 emoji is printed uncolored (ANSI color codes don't apply to
  # color emoji, and a reset right after it would only cut the color
  # short); only the gauge + percentage are wrapped in the threshold color.
  ctx_segment=$(printf " ${DIM}·${RESET} 🧠 ${ctx_color}%s %3d%%${RESET}" "$gauge" "$pct_int")
fi

# Model name (parenthetical suffix, e.g. " (1M context)", stripped to keep the line short)
model_segment=""
model_name=$(echo "$input" | jq -r '.model.display_name // .model.id // empty')
if [ -n "$model_name" ]; then
  model_name=$(printf '%s' "$model_name" | sed -E 's/\([^)]*\)//g; s/^[[:space:]]+//; s/[[:space:]]+$//')
  if [ -n "$model_name" ]; then
    # 🤖 stays uncolored for the same reason as 🧠 above; only the name is dimmed.
    model_segment=$(printf " ${DIM}·${RESET} 🤖 ${DIM}%s${RESET}" "$model_name")
  fi
fi

printf "${GREEN}➜${RESET}  ${CYAN}%s${RESET}%s%s%s\n" "$dir_name" "$git_segment" "$ctx_segment" "$model_segment"
