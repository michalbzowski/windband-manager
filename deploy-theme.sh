#!/usr/bin/env bash
#
# deploy-theme.sh — Wdraża motyw Keycloak "bandmanager" na serwis Railway.
#
# Co robi (idempotentnie):
#   1. Buduje bandmanager-keycloak-theme.jar (Maven) z themes/bandmanager
#   2. Usuwa stary folder /opt/keycloak/themes/bandmanager (gdyby wrócił)
#   3. Wgrywa JAR do /opt/keycloak/providers/ przez SSH (base64 pipe)
#   4. Restartuje Keycloak (kill PID 1 -> Railway auto-restart kontenera)
#
# Dlaczego JAR w providers/, a nie folder w themes/?
#   Surowy folder w /opt/keycloak/themes/bandmanager NADPISUJE JAR w providers/
#   i przywraca stare błędy (np. ${locale.locale} -> HTTP 500 w KC26).
#   JAR w providers/ to jedyny override-proof sposób.
#
# Wymagania:
#   - railway CLI zalogowane (railway whoami)
#   - ssh klucz dodany do Railway (railway ssh keys add)
#   - Maven (mvn) na PATH lub w ~/.m2/wrapper/dists/*/bin/mvn
#   - serwis o nazwie "keycloak" zlinkowany w railway
#
# Użycie:
#   ./deploy-theme.sh
#   RAILWAY_SERVICE=keycloak MVN_BIN=/path/to/mvn ./deploy-theme.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

RAILWAY_SERVICE="${RAILWAY_SERVICE:-keycloak}"
THEME_DIR="themes/bandmanager"

# --- znajdz mvn --------------------------------------------------------------
if command -v mvn >/dev/null 2>&1; then
  MVN="mvn"
elif [ -x "$MVN_BIN" ]; then
  MVN="$MVN_BIN"
else
  MVN="$(ls -d ~/.m2/wrapper/dists/apache-maven-*/bin/mvn 2>/dev/null | head -1)"
  if [ -z "$MVN" ]; then
    echo "ERROR: nie znaleziono mvn (PATH ani ~/.m2/wrapper/dists)" >&2
    exit 1
  fi
fi
echo "[OK] Maven: $MVN"

# --- 1. buduj JAR ------------------------------------------------------------
echo "[1/4] Buduję theme JAR..."
( cd "$THEME_DIR" && "$MVN" -B -q clean package -DskipTests )
JAR="$THEME_DIR/target/bandmanager-keycloak-theme.jar"
[ -f "$JAR" ] || { echo "ERROR: brak $JAR po buildzie" >&2; exit 1; }
echo "[OK] JAR: $JAR ($(wc -c < "$JAR") bajtów)"

# --- 2. usun stary folder (gdyby istnial) -----------------------------------
echo "[2/4] Usuwam stary folder themes/bandmanager (jeśli istnieje)..."
railway ssh -s "$RAILWAY_SERVICE" "rm -rf /opt/keycloak/themes/bandmanager && echo USUNIETO" 2>&1 | tail -1

# --- 3. wgraj JAR do providers/ ---------------------------------------------
echo "[3/4] Wgrywam JAR do /opt/keycloak/providers/..."
base64 "$JAR" | railway ssh -s "$RAILWAY_SERVICE" \
  "base64 -d > /opt/keycloak/providers/bandmanager-keycloak-theme.jar && echo JAR_WRZUCONY && ls -la /opt/keycloak/providers/bandmanager-keycloak-theme.jar" \
  2>&1 | tail -2

# --- 4. restart Keycloak (kill PID 1) ---------------------------------------
echo "[4/4] Restartuję Keycloak (kill PID 1 -> Railway auto-restart)..."
railway ssh -s "$RAILWAY_SERVICE" "kill -TERM 1" 2>&1 | tail -1

echo ""
echo "=== Gotowe! Keycloak wstaje (ok. 30-60s). ==="
echo "Sprawdź: https://login.bandmanager.pl/realms/windband/protocol/openid-connect/auth"
echo "(Uwaga: keycloak.michalbzowski.pl NIE jest domeną Railway — używaj login.bandmanager.pl)"
