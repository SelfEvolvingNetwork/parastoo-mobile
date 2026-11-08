#!/usr/bin/env bash
# بستهٔ نصب آجنت دسترسی را برای assets اپ می‌سازد — همان بسته‌ای که جادوگر
# «افزودن سرور» از داخل APK روی سرور تازه بالا می‌برد و نصب می‌کند.
# استفاده:  bash scripts/pack-agent-bundle.sh [/path/to/access-agent]
set -euo pipefail

SRC="${1:-/home/z/my-project/access-agent}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/assets/provision"
mkdir -p "$OUT"

for f in access_agent install.sh uninstall.sh bootstrap.sh deploy; do
  [ -e "$SRC/$f" ] || { echo "missing: $SRC/$f" >&2; exit 1; }
done

T="$OUT/access-agent-bundle.tar.gz"
tar -czf "$T" -C "$SRC" \
  --exclude='__pycache__' --exclude='*.pyc' --exclude='.git' \
  access_agent install.sh uninstall.sh bootstrap.sh deploy docs

( cd "$OUT" && sha256sum access-agent-bundle.tar.gz | awk '{print $1}' > access-agent-bundle.sha256 )
echo "packed: $T ($(stat -c%s "$T") bytes)"
echo "sha256: $(cat "$OUT/access-agent-bundle.sha256")"
