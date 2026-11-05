#!/usr/bin/env bash
# agent-quick.sh — szybka petla agenta; NIE zastepuje agent-verify.sh przed pushem.
# Kolejnosc: checkstyle -> kompilacja+testy (bez webui/IT/Jasper) -> SpotBugs.
# Uzycie:
#   scripts/agent-quick.sh                          # wszystkie unit testy poza Selenium
#   TEST=MemberServiceTest scripts/agent-quick.sh   # waski target
# Udokumentowane pulapki, ktore ten skrypt ponizsza mechanicznie:
#  - NIE 'surefire:test' (nie re-kompiluje zdrojow) — uzywamy pelnej fazy 'test'
#  - NIE 'mvn -q' (maskuje bledy kompilacji) —pelny log, agent i tak grepnie
#  - forkCount=1 (Selenium 300 MB / instancja; parallel=classes zakazany)
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
if [ -n "${TEST:-}" ]; then
  SEL="-Dtest=$TEST"
else
  SEL="-Dtest=!%regex[.*webui.*],!%regex[.*ReportCompilation.*]"
fi
time ./mvnw -B checkstyle:check test spotbugs:check "$SEL" -Dsurefire.forkCount=1
echo "[agent-quick] OK — pelna bramka przed pushem to scripts/agent-verify.sh"
