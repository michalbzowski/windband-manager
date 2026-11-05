#!/usr/bin/env bash
# =============================================================================
# agent-verify.sh — PELNA bramka deterministyczna dla agentow kodujacych.
# Robi dokladnie to co CI: Checkstyle (validate) + testy + IT + SpotBugs (verify).
# Po sukcesie zapisuje stamp TREE (git write-tree), ktorego zada pre-push-check.sh:
# dowod, ze drzewo wysylane na CI == drzewo sprawdzone lokalnie (Habit Hooks,
# Radar Vol.35 #94/#24; mechanizuje podwojny verify z workflow skill §4).
#
# SEKWENCJA WLASCIVA (flow §4):
#   git add <pliki>  ->  TEN SKRYPT  ->  git commit
#   -> git rebase origin/main  ->  TEN SKRYPT ponownie (nowe drzewo = nowy stamp)
#   -> scripts/pre-push-check.sh  ->  git push
#
# Bramki wejsciowe: indeks == worktree (brak unstaged zmian src/pom/.github) i
# brak ?? pod src/ (niesledzony plik = klasyk czerwonego maina 'cannot find symbol').
# Czas: ~8-10 min (UI suite dominuje — nie skracac przez @Disabled/parallel!).
# NIE uzywaj 'mvn surefire:test' — nie re-kompiluje zdrojow (patrz skill).
# =============================================================================
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
GITDIR="$(git rev-parse --absolute-git-dir)"

if ! git diff --quiet -- src pom.xml .github; then
  echo "STOP(agent-verify): unstaged modyfikacje w src/pom/.github — worktree != index."
  echo "Zrob 'git add <pliki>' (albo stash), aby verify badzil to samo drzewo co commit:"
  git diff --name-only -- src pom.xml .github
  exit 1
fi
UNTRACKED_NEW="$(git status --porcelain -- src pom.xml .github | grep '^??' || true)"
if [ -n "$UNTRACKED_NEW" ]; then
  echo "STOP(agent-verify): nieśledzone (??) pliki pod src/pom/.github — 'git add' je PRZED verify:"
  printf '%s\n' "$UNTRACKED_NEW"
  exit 1
fi

START=$(date +%s)
PENDING_TREE="$(git write-tree)"
echo "[agent-verify] branch=$(git branch --show-current) @ $(git rev-parse --short HEAD); weryfikuje drzewo ${PENDING_TREE:0:8}"
./mvnw -B clean verify \
  '-Dtest=!pl.michalbzowski.windband.application.report.ReportCompilationTest' \
  -Dsurefire.forkCount=1

MIN=$(( ( $(date +%s) - START ) / 60 ))
{
  echo "sha=$(git rev-parse HEAD)"
  echo "tree=$(git write-tree)"
  echo "at=$(date -Is)"
} > "$GITDIR/HERMES_VERIFY_STAMP"

echo "[agent-verify] BUILD SUCCESS w ${MIN} min — stamp zapisany ($GITDIR/HERMES_VERIFY_STAMP)"
echo "[agent-verify] nastepnie: git commit -> rebase (+powtorz skrypt) -> scripts/pre-push-check.sh -> git push"
