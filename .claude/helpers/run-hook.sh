#!/bin/bash
# Cross-platform hook runner - avoids /bin/bash trying to execute cmd /c
# Usage: run-hook.sh <handler> <command> [args...]
HANDLER="$1"
shift

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.."; pwd)}"
USER_DIR="${USERPROFILE//\\//}"

HANDLER_PATH=""
if [ -f "$PROJECT_DIR/.claude/helpers/$HANDLER" ]; then
  HANDLER_PATH="$PROJECT_DIR/.claude/helpers/$HANDLER"
elif [ -f "$USER_DIR/.claude/helpers/$HANDLER" ]; then
  HANDLER_PATH="$USER_DIR/.claude/helpers/$HANDLER"
fi

if [ -n "$HANDLER_PATH" ]; then
  node "$HANDLER_PATH" "$@" 2>/dev/null
fi
exit 0
