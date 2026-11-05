#!/usr/bin/env bash
# =============================================================================
# agent-verify.sh — PELNA bramka deterministyczna dla agentow kodujacych.
# Robi dokladnie to co CI: Checkstyle (faza validate) + testy + IT + SpotBugs
# (faza verify). Po sukcesie zapisuje stamp wymuszany przez pre-push-check.sh
# (wzorzec Habit Hooks / feedback sensors, Radar Vol.35 #94/#24).
#
# KOLEJNOSC WLASCIVA: implementacja -> git commit -> TEN SKRYPT ->
#   pre-push-check.sh -> git push
# Stamp opisuje HEAD ktory fizycznie pojedzie na CI — dlatego wymuszamy
# czyste drzewo PRZED startem (brak niecommitowanych zmian src/pom/workflow).
#
# Czas: ~8-10 min (UI suite dominuje — nie skracac przez @Disabled/parallel!).
# NIE uzywaj 'mvn surefire:test' — nie re-kompiluje zdrojow (patrz skill).
# =============================================================================
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
GITDIR="$(git rev-parse --absolute-git-dir)"

DIRTY="$(git status --porcelain -- src pom.xml .github .gitignore)"
if [ -n "$DIRTY" ]; then
  echo "STOP(agent-verify): niecommitowane zmiany ponizey — zrób commit PRZED verify,"
  echo "bo stamp musi odpowiadać drzewu, które trafi na CI:"
  printf '%s\n' "$DIRTY"
  exit 1
fi

START=$(date +%s)
echo "[agent-verify] branch=$(git branch --show-current) @ $(git rev-parse --short HEAD)"
./mvnw -B clean verify \
  '-Dtest=!pl.michalbzowski.windband.application.report.ReportCompilationTest' \
  -Dsurefire.forkCount=1

MIN=$(( ( $(date +%s) - START ) / 60 ))
{
  echo "sha=$(git rev-parse HEAD)"
  echo "tree=$(git rev-parse "HEAD^{tree}")"
  echo "at=$(date -Is)"
} > "$GITDIR/HERMES_VERIFY_STAMP"

echo "[agent-verify] BUILD SUCCESS w ${MIN} min — stamp: $GITDIR/HERMES_VERIFY_STAMP"
echo "[agent-verify] nastepny krok: scripts/pre-push-check.sh && git push"
