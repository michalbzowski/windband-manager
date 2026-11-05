#!/usr/bin/env bash
# pipeline-lint.sh — zizmor lokalnie PRZED edycja .github/workflows (Radar Vol.35 #103).
# To samo egzekwuje CI job 'pipeline-lint'. Instalacja: uv tool install zizmor
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
ZIZMOR="${ZIZMOR_BIN:-$HOME/.local/bin/zizmor}"
[ -x "$ZIZMOR" ] || ZIZMOR="$(command -v zizmor || true)"
[ -n "$ZIZMOR" ] || { echo "zainstaluj: uv tool install zizmor"; exit 2; }
"$ZIZMOR" . --min-severity high --no-online-audits
