#!/usr/bin/env bash
# pipeline-run.sh — odpal wybrany job CI lokalnie przez Act (nektos/act, Radar Vol.35 #71).
# Daje agentowi fail CI zanim wejdzie do wspolnego pipeline'u (agent pipeline responsibility).
# Uzycie:
#   scripts/pipeline-run.sh                 # build, pull_request
#   scripts/pipeline-run.sh pipeline-lint   # lint workflowow (szybki, < 1 min)
#   scripts/pipeline-run.sh pit             # mutation testing jak na CI
# Wymaga: docker + act (gh release download --repo nektos/act --pattern '*Linux_x86_64*' -O act.tgz).
# PIERWSZY run sciagnie obraz runnera (GB) — kolejne z --reuse sa tanie.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
JOB="${1:-build}"
EVENT="${2:-pull_request}"
ACT="${ACT_BIN:-$HOME/.local/bin/act}"
[ -x "$ACT" ] || { echo "zainstaluj act do ~/.local/bin (patrz naglowek skryptu)"; exit 2; }
docker info >/dev/null 2>&1 || { echo "Docker niedostepny — Act wymaga dockera."; exit 2; }
exec "$ACT" "$EVENT" --job "$JOB" --reuse \
  -s GITHUB_TOKEN="$(gh auth token)" \
  --artifact-server-path /tmp/act-artifacts
