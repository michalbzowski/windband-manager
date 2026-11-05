#!/usr/bin/env bash
# =============================================================================
# pre-push-check.sh — mechaniczna bramka PRZED kazdym git push (Radar Vol.35 #24,
# #3 + lokalne regula 'red main = untracked deps').
#
# Blokuje, gdy:
#  1) untracked (??) pliki pod src/ — glowna przyczyna czerwonego maina
#     (CI 'cannot find symbol' z powodu pliku, ktory zostal lokalnie);
#  2) stamp agent-verify.sh nie zgadza sie z HEAD — kod zmieniony po verify
#     albo rebase przestawil SHA (swiadomie: wymagany ponowny verify);
#  3) CI dla tego SHA na tym branchu jest czerwony — napraw na TYM SAMYM branchu.
#
# 0 = mozna pushnac; 1 = blokada (powody na stdout).
# Awaryjne ominięcie: VERIFY_OVERRIDE=1 / CI_OVERRIDE=1 + OVERRIDE_REASON="<powod>"
# (powod trafi do opisu PR — brak powodu = naduzycie).
# =============================================================================
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
GITDIR="$(git rev-parse --absolute-git-dir)"
BR="$(git branch --show-current)"
HEAD_SHA="$(git rev-parse HEAD)"
FAIL=0

echo "[pre-push-check] branch=$BR @ $(git rev-parse --short HEAD)"

# --- 1) nieotswarzone pliki pod src/ ---
SRC_UNTRACKED="$(git status --porcelain | grep '^?? src/' || true)"
if [ -n "$SRC_UNTRACKED" ]; then
  echo "BLOKADA(1): untracked (??) pliki pod src/ — 'git add' albo usun:"
  printf '%s\n' "$SRC_UNTRACKED"
  FAIL=1
fi
OTHER_UNTRACKED="$(git status --porcelain | grep '^??' | grep -v '^?? src/' || true)"
if [ -n "$OTHER_UNTRACKED" ]; then
  echo "UWAGA(1): untracked poza src/ (czy nie powinny do gita?):"
  printf '%s\n' "$OTHER_UNTRACKED"
fi

# --- 2) stamp agent-verify: drzewo commita == drzewo zweryfikowane ---
STAMP="$GITDIR/HERMES_VERIFY_STAMP"
STAMP_TREE=""
[ -f "$STAMP" ] && STAMP_TREE="$(grep '^tree=' "$STAMP" | cut -d= -f2)"
HEAD_TREE="$(git rev-parse "HEAD^{tree}")"
if [ -n "$STAMP_TREE" ] && [ "$STAMP_TREE" = "$HEAD_TREE" ]; then
  echo "OK(2): drzewo HEAD identyczne z drzewem przejdzym agent-verify (tree ${HEAD_TREE:0:8})"
elif [ "${VERIFY_OVERRIDE:-}" = "1" ]; then
  echo "OVERRIDE(2): VERIFY_OVERRIDE=1 — powod: ${OVERRIDE_REASON:-PODaj OVERRIDE_REASON!}"
else
  echo "BLOKADA(2): drzewo HEAD (${HEAD_TREE:0:8}) != drzewo ze stampu (${STAMP_TREE:0:8:-8}). Rebase/nowe zmiany = wymuszony ponowny scripts/agent-verify.sh."
  FAIL=1
fi

# --- 3) CI tego SHA nie moze byc czerwony ---
run_field() { # $1 = pole JSON, $2 = domyslny indeks
  printf '%s' "$RUN" | python3 -c "import json,sys
try:
    d = json.load(sys.stdin)
    print(d[0].get('$1','') if d else '')
except Exception:
    print('')"
}
RUN="$(gh run list --branch "$BR" --limit 1 --json status,conclusion,headSha 2>/dev/null || echo '[]')"
R_STATUS="$(run_field status)"; R_CONC="$(run_field conclusion)"; R_SHA="$(run_field headSha)"

if [ -z "$R_STATUS" ]; then
  echo "OK(3): brak runow CI dla $BR (pierwszy push — po pushu: scripts/ci-status.sh)"
elif [ "$R_STATUS" != "completed" ]; then
  echo "UWAGA(3): CI w toku dla $BR ($R_STATUS) — po pushu sledz scripts/ci-status.sh"
elif [ "$R_SHA" = "$HEAD_SHA" ] && [ "$R_CONC" != "success" ]; then
  if [ "${CI_OVERRIDE:-}" = "1" ]; then
    echo "OVERRIDE(3): CI_OVERRIDE=1 — powod: ${OVERRIDE_REASON:-PODaj OVERRIDE_REASON!}"
  else
    echo "BLOKADA(3): CI czerwony ($R_CONC) dla HEAD — napraw na tym samym branchu (nie zakladaj nowego)."
    FAIL=1
  fi
else
  echo "OK(3): ostatni run CI nie dotyczy HEAD lub byl zielony (${R_CONC:-?} @ ${R_SHA:0:8})"
fi

# --- 4) swiezos origin/main (rebase to wymog workflow) ---
git fetch origin --quiet 2>/dev/null || true
if [ "$BR" != "main" ] && ! git merge-base --is-ancestor origin/main HEAD 2>/dev/null; then
  echo "UWAGA(4): branch nie zawiera najnowszego origin/main — 'git rebase origin/main' + ponowny verify przed pushem."
fi

if [ "$FAIL" = "1" ]; then
  echo "[pre-push-check] WYNIK: ZABLOKOWANY"
  exit 1
fi
echo "[pre-push-check] WYNIK: OK"
