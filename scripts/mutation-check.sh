#!/usr/bin/env bash
# mutation-check.sh — PIT lokalnie (Radar Vol.35 #2, Adopt). Straze testow agentowych.
# Uzycie:
#   scripts/mutation-check.sh 'pl.michalbzowski.windband.domain.member.*'   # wasko (zalecane)
#   scripts/mutation-check.sh 'a.b.*,c.d.*'                                  # wiele pakietow
#   scripts/mutation-check.sh                                                # pelny baseline (WOLNE!)
# CI robi PIT automatycznie dla pakietow ruszonych w danej zmianie (job 'pit').
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
CLASSES="${1:-}"
if [ -n "$CLASSES" ]; then
  EXTRA="-Dpit.target.classes=$CLASSES"
else
  EXTRA=""
  echo "[pit] pelny baseline (domain+application+util) — z zapisana historia powinien byc szybki;"
  echo "[pit] przy przerwaniu odpal ponownie z '-DwithHistory' (domyślne w profilu pit)."
fi
./mvnw -B -Ppit test-compile org.pitest:pitest-maven:mutationCoverage $EXTRA
IDX=$(ls -1dt target/pit-reports/*/ 2>/dev/null | head -1 || true)
echo "[pit] raport: ${IDX:-target/pit-reports/}index.html — prog gates: pit.mutation.threshold w pom.xml (profil pit)"
