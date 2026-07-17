#!/usr/bin/env bash
#
# setup-theme.sh — LOKALNY dev-only: wdraża motyw przez bind-mount (docker-compose)
#
# ⚠️  PRODUKCJA (Railway): motyw NIE jest tu wdrażany.
#     Railway buduje `Dockerfile.keycloak`, który piecze JAR motywu do
#     /opt/keycloak/providers/ (zob. railway.toml). Push na `main` wdraża
#     poprawki motywu automatycznie — bez ręcznego uploadu JAR.
#
#     Uwaga: nigdy nie kładź surowego katalogu w /opt/keycloak/themes/bandmanager
#     w produkcji — nadpisuje on JAR w providers/ i przywraca stare błędy
#     (np. ${locale.locale} → HTTP 500).
#
# Użycie (tylko lokalnie):
#   chmod +x setup-theme.sh
#   ./setup-theme.sh
#
# Co robi skrypt:
#   1. Sprawdza, czy motyw istnieje lokalnie
#   2. Restartuje kontener Keycloaka (docker-compose montuje ./themes :ro)
#   3. Włącza motyw bandmanager przez kcadm

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

THEME_DIR="themes/bandmanager"
CONTAINER="windband-keycloak"

echo "=== Band Manager — Keycloak Theme Setup ==="

# 1. Sprawdź czy katalog motywu istnieje
if [ ! -d "$THEME_DIR" ]; then
    echo "ERROR: Katalog motywu '$THEME_DIR' nie istnieje!"
    exit 1
fi

echo "[OK] Motyw znaleziony w $THEME_DIR"

# 2. Sprawdź czy PicoCSS jest pobrany
if [ ! -f "$THEME_DIR/login/resources/css/pico.min.css" ]; then
    echo "[INFO] Pobieram PicoCSS..."
    mkdir -p "$THEME_DIR/login/resources/css"
    curl -sL -o "$THEME_DIR/login/resources/css/pico.min.css" \
        "https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css"
    echo "[OK] PicoCSS pobrany"
else
    echo "[OK] PicoCSS już istnieje"
fi

# 3. Restartuj Keycloaka
echo "[INFO] Restartuję kontener $CONTAINER..."
docker restart "$CONTAINER"

# 4. Czekaj aż Keycloak wstanie
echo "[INFO] Czekam na gotowość Keycloaka..."
for i in $(seq 1 30); do
    if docker exec "$CONTAINER" wget -qO- http://localhost:8180/health/ready 2>/dev/null; then
        echo "[OK] Keycloak gotowy"
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "WARNING: Keycloak nie odpowiada po 30 próbach — sprawdź logi: docker logs $CONTAINER"
    fi
    sleep 2
done

# 5. Włącz motyw via kcadm
echo "[INFO] Logowanie do Keycloaka admin CLI..."
sleep 3  # dodatkowy czas na pełne uruchomienie

docker exec "$CONTAINER" /opt/keycloak/bin/kcadm.sh config credentials \
    --server http://localhost:8180 \
    --realm master \
    --user "${KEYCLOAK_ADMIN:-admin}" \
    --password "${KEYCLOAK_ADMIN_PASSWORD:-admin}" 2>/dev/null || echo "[WARN] Nie udało się zalogować przez CLI — ustaw motyw ręcznie."

# Ustaw motyw
docker exec "$CONTAINER" /opt/keycloak/bin/kcadm.sh update realms/master \
    -s loginTheme=bandmanager 2>/dev/null || echo "[WARN] Nie udało się ustawić motywu przez CLI."

echo ""
echo "=== Gotowe! ==="
echo "Motyw 'bandmanager' jest zainstalowany."
echo "Jeśli logowanie Keycloaka nie ładuje się automatycznie,"
echo "zaloguj się do konsoli admin: http://localhost:8180/admin"
echo "i ustaw: Realm Settings → Themes → Login Theme: bandmanager"
echo ""
echo "W przypadku development mode — zmiany w plikach .ftl i .css"
echo "są widoczne od razu po refresh przeglądarki (bez restartu)."