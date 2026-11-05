#!/usr/bin/env bash
# ci-status.sh — status CI dla agenta (Radar Vol.35 #24 'agent pipeline responsibility').
# Zasada: agent, ktory nie widzi stanu builda, nie respektuje zakazu push na czerwono.
# Uzycie:
#   scripts/ci-status.sh                 # obecna galaz + main
#   scripts/ci-status.sh <branch>        # wskazana galaz
#   scripts/ci-status.sh --logs <run_id> # tail logow faila danego runa
set -euo pipefail

if [ "${1:-}" = "--logs" ]; then
  RID="${2:?usage: ci-status.sh --logs RUN_ID}"
  gh run view "$RID" --log-failed | tail -n 200
  exit 0
fi

BR="${1:-$(git branch --show-current 2>/dev/null || echo main)}"
echo "== ostatnie runy: $BR =="
gh run list --branch "$BR" --limit 5 \
  --json status,conclusion,event,displayTitle,createdAt,url
echo "== main (ostatnie 3) =="
gh run list --branch main --limit 3 \
  --json status,conclusion,event,headSha,createdAt,url
