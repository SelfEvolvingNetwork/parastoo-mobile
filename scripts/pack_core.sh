#!/usr/bin/env bash
# pack_core.sh — ساخت بستهٔ هستهٔ پرستو موبایل از AAR ماژول :coreimpl
#
#   pack_core.sh <coreimpl-debug.aar> <run_number>
#
# خروجی‌ها (در core/build/ و نسخهٔ جاسازی‌شده در app/src/main/assets/core/):
#   parastoo-core.zip            ← بستهٔ هسته (manifest.json + classes.dex)
#   parastoo-core.zip.sha256     ← کانال دوم صحت (کنار دارایی رلیز)
#   app/src/main/assets/core/core-bundle.zip   ← خط پایین اطمینان داخل APK
#
# قرارداد مانیفست: foundation/.../core/contract/AppCore.kt (AppCoreManifest)
# توزیع قراردادی بسته دقیقاً دو عضو دارد؛ بازکننده‌های سمت اپ سخت‌گیرانه‌اند.
set -euo pipefail

AAR="${1:?مسیر AAR هسته لازم است}"
RUN="${2:?شمارهٔ ران CI لازم است}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/core/build"
WORK="$OUT/pack-work"
APP_BLD="$ROOT/app/build.gradle.kts"

rm -rf "$WORK"; mkdir -p "$OUT" "$WORK"
unzip -q -o "$AAR" classes.jar -d "$WORK"

# --- ابزارهای اندروید: d8 + android.jar (بعد از بیلد گرِیدل نصب‌شده‌اند) ---
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
D8="$(ls "$SDK"/build-tools/*/d8 2>/dev/null | sort -V | tail -1 || true)"
PLATFORM="$(ls -d "$SDK"/platforms/android-* 2>/dev/null | sort -V | tail -1 || true)"
[ -n "$D8" ] || { echo "خطا: d8 از build-tools پیدا نشد (SDK=$SDK)" >&2; exit 1; }
[ -n "$PLATFORM" ] && [ -f "$PLATFORM/android.jar" ] || { echo "خطا: android.jar پیدا نشد" >&2; exit 1; }
echo "d8: $D8"
echo "platform: $PLATFORM"

# --- classes.jar → classes.dex (min-api 26 مثل خود APK) ---
"$D8" --release --min-api 26 --lib "$PLATFORM/android.jar" \
  --output "$WORK" "$WORK/classes.jar"
[ -f "$WORK/classes.dex" ] || { echo "خطا: d8 خروجی classes.dex نداد" >&2; exit 1; }

# --- نسخهٔ بدنه از build.gradle.kts بدنه (منبعِ واحد نسخه) ---
VN="$(grep -oE 'versionName = "[^"]+"' "$APP_BLD" | head -1 | cut -d'"' -f2)"
VC="$(grep -oE 'versionCode = [0-9]+' "$APP_BLD" | head -1 | awk '{print $3}')"
[ -n "$VN" ] && [ -n "$VC" ] || { echo "خطا: نسخهٔ بدنه خوانده نشد" >&2; exit 1; }

# --- مانیفست بسته + zip + هش ---
DEX_SHA="$(sha256sum "$WORK/classes.dex" | awk '{print $1}')"
python3 - "$WORK" "$RUN" "$VN" "$VC" "$DEX_SHA" << 'PY'
import json, sys
work, run, vn, vc, dex_sha = sys.argv[1], int(sys.argv[2]), sys.argv[3], int(sys.argv[4]), sys.argv[5]
m = {
    "schema": "parastoo-core-bundle/1",
    "revision": run,            # freshness — از شمارهٔ ران CI
    "version_code": vc,         # هم‌خط با بدنهٔ همزمان (اطلاعاتی)
    "version_name": vn,
    "min_body_version_code": vc,  # این هسته هم‌زمانِ این بدنه ساخته شده
    "entry_class": "network.selfevolving.parastoo.mobile.core.appcore.ParastooCoreEntry",
    "dex_sha256": dex_sha,
}
with open(f"{work}/manifest.json", "w") as f:
    json.dump(m, f, indent=2)
print(json.dumps(m, indent=2))
PY

# zip با فقط دو عضو قراردادی (بازکننده‌های سمت اپ هر عضو دیگری را رد می‌کنند)
rm -f "$OUT/parastoo-core.zip"
(cd "$WORK" && zip -q -X "$OUT/parastoo-core.zip" manifest.json classes.dex)
sha256sum "$OUT/parastoo-core.zip" | awk '{print $1}' > "$OUT/parastoo-core.zip.sha256"

# --- جاسازی داخل APK (خط پایین اطمینان — rollback لودر به آن برمی‌گردد) ---
mkdir -p "$ROOT/app/src/main/assets/core"
cp "$OUT/parastoo-core.zip" "$ROOT/app/src/main/assets/core/core-bundle.zip"

echo "بستهٔ هسته ساخته شد: $OUT/parastoo-core.zip ($(wc -c < "$OUT/parastoo-core.zip") بایت، dex sha ${DEX_SHA:0:16}…)"
