#!/usr/bin/env python3
"""dex_forensics.py — مقایسهٔ مرجع‌های خارجی dex هسته با کلاس‌های تعریف‌شده در APK بدنه.

Usage:
  dex_forensics.py <core_classes.dex> <body.apk> [--bundled-asset]

خروجی:
  - نسخهٔ dex و تعداد کلاس‌های تعریف‌شده
  - همهٔ typeهای مرجع‌شدهٔ خارجی (نه تعریف در dex هسته، نه boot classpath شناخته‌شده)
  - کدام‌یک از آن‌ها در dexهای APK بدنه تعریف نشده‌اند = نامزدهای شکست لود/کرش
  - با --gate: اگر کلاس مرجعِ L-دسکریپتوری در بدنه نباشد exit 1 (گیت CI؛ درس rev31)
"""
import struct, sys, zipfile, io

BOOT_PREFIXES = (
    "Ljava/", "Ljavax/", "Landroid/", "Lcom/android/", "Lorg/apache/harmony/",
    "Lcom/mediatek/", "Ldalvik/", "Lkotlin/", "Lkotlinx/coroutines/", "Lorg/json/",
    "Lorg/xmlpull/", "Lj$",  # desugared lib
)


class Dex:
    def __init__(self, data: bytes):
        self.data = data
        magic = data[:8]
        self.version = magic[4:8].decode("ascii", "replace")
        assert magic[:4] == b"dex\n", f"bad magic {magic!r}"
        # header: magic[8] checksum[4] signature[20] then 20 u32 fields (offset 32)
        (self.file_size, self.header_size, self.endian_tag, self.link_size,
         self.link_off, self.map_off, self.string_ids_size, self.string_ids_off,
         self.type_ids_size, self.type_ids_off, self.proto_ids_size,
         self.proto_ids_off, self.field_ids_size, self.field_ids_off,
         self.method_ids_size, self.method_ids_off, self.class_defs_size,
         self.class_defs_off, self.data_size, self.data_off) = struct.unpack_from(
            "<20I", data, 32)
        self._strings = None

    @property
    def strings(self):
        if self._strings is not None:
            return self._strings
        out = []
        for i in range(self.string_ids_size):
            off = struct.unpack_from("<I", self.data, self.string_ids_off + 4 * i)[0]
            # uleb128 utf16_size — then MUTF-8 bytes terminated by 0x00
            # (modified UTF-8 encodes U+0000 as C0 80, so raw 0x00 = terminator)
            n = 0
            shift = 0
            p = off
            while True:
                b = self.data[p]
                p += 1
                n |= (b & 0x7F) << shift
                if b & 0x80 == 0:
                    break
                shift += 7
            end = self.data.index(b"\x00", p)
            out.append(self.data[p:end].decode("utf-8", "replace"))
        self._strings = out
        return out

    @property
    def types(self):
        # type_ids → descriptor string index
        descs = []
        for i in range(self.type_ids_size):
            sidx = struct.unpack_from("<I", self.data, self.type_ids_off + 4 * i)[0]
            descs.append(self.strings[sidx])
        return descs

    @property
    def defined_classes(self):
        out = set()
        for i in range(self.class_defs_size):
            base = self.class_defs_off + 32 * i
            class_idx, = struct.unpack_from("<I", self.data, base)
            out.add(self.types[class_idx])
        return out


def analyze(core_path, apk_path, cache=None):
    import os, pickle, time
    core = Dex(open(core_path, "rb").read())
    core_defined = core.defined_classes
    print(f"core dex: version={core.version!r} strings={core.string_ids_size} "
          f"types={core.type_ids_size} defined={core.class_defs_size}", flush=True)

    # APK: collect all dexes + assets listing (with pickle cache — parsing is slow)
    zf = zipfile.ZipFile(apk_path)
    names = zf.namelist()
    dex_names = [n for n in names if n.startswith("classes") and n.endswith(".dex")]
    print(f"APK dexes: {dex_names}", flush=True)
    if cache and os.path.exists(cache):
        body_defined, apksizes = pickle.load(open(cache, "rb"))
        print("loaded body classes from cache", flush=True)
    else:
        body_defined = set()
        apksizes = {}
        for dn in dex_names:
            t0 = time.time()
            raw = zf.read(dn)
            d = Dex(raw)
            body_defined |= d.defined_classes
            apksizes[dn] = d.class_defs_size
            print(f"APK {dn}: defined={d.class_defs_size} ({time.time()-t0:.1f}s)", flush=True)
        if cache:
            pickle.dump((body_defined, apksizes), open(cache, "wb"))
    print(f"APK total defined classes: {len(body_defined)}", flush=True)
    print(f"APK has assets/core/core-bundle.zip: {'assets/core/core-bundle.zip' in names}", flush=True)

    # external references of core = core types not defined in core
    external = set(t for t in core.types if t not in core_defined)
    missing = [t for t in sorted(external) if t not in body_defined]
    # split missing into boot-classpath-likely vs app-scope missing
    missing_boot = [t for t in missing if t.startswith(BOOT_PREFIXES)]
    missing_app = [t for t in missing if not t.startswith(BOOT_PREFIXES)]
    print(f"\ncore external refs: {len(external)}")
    print(f"missing from body APK (boot-classpath-likely): {len(missing_boot)}")
    for t in missing_boot:
        print(f"  [boot?] {t}")
    print(f"missing from body APK (app-scope — REAL RISK): {len(missing_app)}")
    for t in missing_app:
        print(f"  [MISSING] {t}")

    # sanity: entry class defined in core dex?
    entry = "Lnetwork/selfevolving/parastoo/mobile/core/appcore/ParastooCoreEntry;"
    print(f"\nentry class defined in core dex: {entry in core_defined}")
    contract = "Lnetwork/selfevolving/parastoo/mobile/core/contract/AppCoreEntry;"
    print(f"contract AppCoreEntry defined in body APK: {contract in body_defined}")

    # gate mode (CI): app-scope missing CLASS descriptors (L...) = hard failure.
    # درس rev31: بدنه باید همهٔ کلاس‌های مرجعِ هسته را داشته باشد، وگرنه هسته
    # در گوشی لود/رندر نمی‌شود. primitive/array descriptors (بدون L) بی‌معنی‌اند.
    if "--gate" in sys.argv:
        bad = [t for t in missing_app if t.startswith("L")]
        if not entry in core_defined:
            bad.insert(0, entry)
        if not contract in body_defined:
            bad.insert(0, contract)
        if bad:
            print("\nGATE FAILED — این کلاس‌های مرجعِ dex هسته در APK بدنه تعریف نشده‌اند:")
            for t in bad:
                print(f"  [FAIL] {t}")
            sys.exit(1)
        print("\nGATE OK — بدنه همهٔ مرجع‌های کلاسِ هسته را دارد")


if __name__ == "__main__":
    # CI runs: scripts/dex_forensics.py <core_dex> <apk> --gate
    cache = None if "--gate" in sys.argv else "/home/z/my-project/core-debug/body_classes.cache"
    analyze(sys.argv[1], sys.argv[2], cache=cache)
