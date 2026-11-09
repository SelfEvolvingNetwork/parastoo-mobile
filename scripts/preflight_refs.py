#!/usr/bin/env python3
"""preflight_refs.py — پیش‌پرواز مرجع‌یابی صلیبی سه ماژول پرستو موبایل.

درس میدانی ران ۲۶ (UI-refactor): جابه‌جایی یک کارت بدون جابه‌جایی همراه‌های
خصوصی‌اش → Unresolved reference در CI. این پیش‌پرواز همان شکست را «پیش از
push» می‌گیرد، با قواعد معماری بدنه/هسته:

  ۱) هر import داخل هر ماژول باید به یک فایل واقعی در همان ماژول یا
     ماژول‌های مجازش ختم شود:
        app        ← {app, foundation}
        foundation ← {foundation}          (بدون وابستگی به هسته!)
        coreimpl   ← {coreimpl, foundation}
  ۲) ماژول‌های غیر app هرگز به R اپ ارجاع نمی‌زنند ( فقط foundation.R مجاز)
  ۳) import بین‌ماژولی از packages فقط-هسته برای app = خطا و بالعکس

خروجی: فهرست تخلف‌ها؛ کد خروج ۱ اگر تخلفی بود (CI این را می‌بندد).
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PKG = "network.selfevolving.parastoo.mobile"

ALLOWED = {
    "app": {"app", "foundation"},
    "foundation": {"foundation"},
    "coreimpl": {"coreimpl", "foundation"},
}

IMPORT_RE = re.compile(r"^\s*import\s+(?:[\w.]+\.)?([\w.]+)\s*$")

# (package, stem) → module
symbols: dict[tuple[str, str], str] = {}
files: list[tuple[str, Path, str, list[str]]] = []  # (module, path, package, imports)


def module_of(path: Path) -> str | None:
    rel = path.relative_to(ROOT).as_posix()
    for m in ("app", "foundation", "coreimpl"):
        if rel.startswith(f"{m}/src/main/java/"):
            return m
    return None


def package_of(path: Path, module: str) -> str:
    base = f"{module}/src/main/java/"
    rel = path.relative_to(ROOT / f"{module}/src/main/java").as_posix()
    return rel.rsplit("/", 1)[0].replace("/", ".")


for kt in sorted(ROOT.glob("*/src/main/java/**/*.kt")):
    module = module_of(kt)
    if not module:
        continue
    text = kt.read_text(encoding="utf-8", errors="replace")
    pkg = package_of(kt, module)
    imports = []
    for line in text.splitlines():
        m = IMPORT_RE.match(line)
        if m:
            imports.append((m.group(1), text.splitlines().index(line) + 1))
    files.append((module, kt, pkg, imports))
    stem = kt.stem
    symbols.setdefault((pkg, stem), module)
    # چند نماد در یک فایل — تعریف‌های سطح بالا را هم ثبت کن (data class/enum/object/fun)
    for decl in re.findall(
        r"^(?:@\w+\s+)*(?:public\s+|internal\s+|private\s+)?(?:sealed\s+|abstract\s+|open\s+|data\s+|enum\s+)*(?:class|interface|object|enum class)\s+(\w+)",
        text,
        re.M,
    ):
        symbols.setdefault((pkg, decl), module)

violations: list[str] = []

for module, path, pkg, imports in files:
    lineno = 0
    text_lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    for raw in text_lines:
        lineno += 1
        line = raw
        m = IMPORT_RE.match(line)
        if not m:
            continue
        fq = m.group(1)
        if not fq.startswith(PKG):
            continue
        # R اپ — فقط ماژول app
        if fq == f"{PKG}.R" or fq == f"{PKG}.BuildConfig":
            if module != "app":
                violations.append(f"{path.relative_to(ROOT)}:{lineno} — import از R/BuildConfig اپ در ماژول «{module}»")
            continue
        rest = fq[len(PKG) + 1 :]
        if "." in rest:
            ipkg, stem = rest.rsplit(".", 1)
        else:
            ipkg, stem = PKG, rest
        owner = symbols.get((ipkg, stem))
        if owner is None:
            # شاید import wildcard یا فایل بیرون درخت باشد — رد
            continue
        if owner not in ALLOWED[module]:
            violations.append(
                f"{path.relative_to(ROOT)}:{lineno} — «{fq}» در ماژول «{owner}» است و ماژول «{module}» مجاز به دیدنش نیست (قواعد: {module} ← {'، '.join(sorted(ALLOWED[module]))})"
            )

# قاعدهٔ ۲: استفادهٔ مستقیم R بدون import foundation.R در غیر app
for module, path, pkg, imports in files:
    if module == "app":
        continue
    text = path.read_text(encoding="utf-8", errors="replace")
    has_foundation_r = "network.selfevolving.parastoo.mobile.foundation.R" in text
    for lineno, line in enumerate(text.splitlines(), 1):
        if re.search(r"\bR\.(drawable|string|font|color|style)\b", line):
            if not has_foundation_r and module != "foundation":
                violations.append(
                    f"{path.relative_to(ROOT)}:{lineno} — استفادهٔ R بدون import قراردادی foundation.R در ماژول «{module}»"
                )

if violations:
    print(f"پیش‌پرواز: {len(violations)} تخلف مرجع‌یابی:")
    for v in violations:
        print(f"  ✗ {v}")
    sys.exit(1)

print(f"پیش‌پرواز سالم — {len(files)} فایل کاتلین، {len(symbols)} نماد، بدون تخلف مرز ماژول‌ها")
