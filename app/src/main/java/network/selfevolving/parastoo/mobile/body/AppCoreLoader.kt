package network.selfevolving.parastoo.mobile.body

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dalvik.system.DexClassLoader
import java.io.File
import network.selfevolving.parastoo.mobile.core.contract.AppCoreEntry
import network.selfevolving.parastoo.mobile.core.contract.AppCoreIo
import network.selfevolving.parastoo.mobile.core.contract.AppCoreManifest
import network.selfevolving.parastoo.mobile.core.contract.AppCorePrefs
import network.selfevolving.parastoo.mobile.ui.components.SwallowBadge
import org.json.JSONObject

/**
 * لودر هستهٔ اپ — قلب معماری «بدنه ثابت + هستهٔ قابل تعویض»
 * (فرمان مالک 2026-11-09: «بدنه یک بار نصب؛ هسته توسط بدنه بروز شود؛
 * بدون نصب مجدد APK»).
 *
 * کاندیداها (مرتب بر اساس revision نزولی — بیشینهٔ سالم می‌برد):
 *  ۱) هسته‌های OTA در filesDir/core/versions/<revision>/ (آپدیتر هسته می‌نویسد)
 *  ۲) باندل داخل APK در assets/core/core-bundle.zip (خط پایین اطمینان؛ CI می‌سازد)
 *
 * برای هر کاندیدا:
 *  - مانیفست سخت‌گیرانه پارس می‌شود (قرارداد AppCoreManifest)
 *  - sha256 خودِ classes.dex با مانیفست بسته می‌سنجد (لایهٔ اول؛ لایهٔ دوم —
 *    هش کل zip — پیش از نصب در آپدیتر هسته سنجیده شده است)
 *  - گارد سازگاری: min_body_version_code ≤ versionCode بدنه
 *  - گارد سلامت: دو بوت پیاپی ناموفق به تفکیک (revision، منبع) = کنار گذاشتن
 *    (AppCorePrefs.MAX_FAILS) — درس rev31: شمارش مشترکِ OTA/باندل، فالبکِ
 *    سالم را هم مسموم می‌کرد؛ شمارش مشترک حذف شد
 *  - گارد سلامتِ رندر (درس rev31): اگر پروسه در بووت قبلی پیش از اولین فریم
 *    موفقِ هسته مُرده بود (مثل NoClassDefFoundError در Compose)، بووتِ جدید
 *    آن را یک شکست می‌بُرد — کرش‌لوپ هم به فالبک می‌رسد
 *  - خطای واقعی هر شکست در prefs می‌ماند (KEY_LAST_FAIL_*) تا صفحهٔ صادقانهٔ
 *    بدنه فقط پیام مبهم «کنار گذاشته شد» نگوید، علت را هم بگوید
 *  - DexClassLoader با parent-first (والد = کلاس‌لودر بدنه)؛ هسته به بنیاد،
 *    کتابخانه‌ها و ریسورس‌های بدنه می‌رسد و هیچ چیز دوباره بارد نمی‌شود
 *
 * این کلاس هیچ وابستگی به کلاس‌های هسته ندارد — فقط قراردادِ :foundation.
 */
object AppCoreLoader {

    private const val TAG = "ParastooCoreLoader"

    sealed class Result {
        /** هسته آماده — بدنه فقط App() را در تم خودش صدا می‌زند */
        data class Loaded(
            val entry: AppCoreEntry,
            val revision: Int,
            val source: String,
            val sourceTag: String, // "ota" یا "bundle" — برای گارد سلامت رندر
        ) : Result()

        /** هیچ هسته‌ای سالم لود نشد — صفحهٔ صادقانهٔ بدنه */
        data class Failed(val reason: String) : Result()
    }

    /** نسخهٔ بدنه — حقیقت نصب‌شده، بدون BuildConfig */
    fun bodyVersionCode(context: Context): Long = AppCoreIo.bodyVersionCode(context)

    /**
     * نشانگر «این هسته در حال رندر است» — MainActivity پیش از اولین
     * فریم هسته صدا می‌زند؛ اگر پروسه پیش از markRenderOk مُرد، بووتِ بعدی
     * این را یک شکست واقعی می‌بُرد (درس rev31: کرشِ رندر نباید بی‌شمارش بماند).
     */
    fun markBootPending(context: Context, revision: Int, sourceTag: String) {
        context.getSharedPreferences(AppCorePrefs.FILE, Context.MODE_PRIVATE).edit()
            .putInt(AppCorePrefs.KEY_BOOT_PENDING_REV, revision)
            .putString(AppCorePrefs.KEY_BOOT_PENDING_SOURCE, sourceTag)
            .putBoolean(AppCorePrefs.KEY_BOOT_PENDING_OK, false)
            .apply()
    }

    /** اولین فریم هسته سومان گرفت — نشانگر pending را سبز می‌کند */
    fun markRenderOk(context: Context, revision: Int) {
        context.getSharedPreferences(AppCorePrefs.FILE, Context.MODE_PRIVATE).edit()
            .putInt(AppCorePrefs.KEY_BOOT_PENDING_REV, revision)
            .putBoolean(AppCorePrefs.KEY_BOOT_PENDING_OK, true)
            .apply()
    }

    /**
     * رسیدگی به بووت قبلی: اگر پروسه پیش از اولین فریم موفق مُرده بود،
     * شکستِ همان (rev، منبع) ثبت می‌شود و نشانگر پاک می‌شود.
     */
    private fun settlePendingBoot(prefs: android.content.SharedPreferences) {
        val pendingRev = prefs.getInt(AppCorePrefs.KEY_BOOT_PENDING_REV, 0)
        if (pendingRev <= 0) return
        val ok = prefs.getBoolean(AppCorePrefs.KEY_BOOT_PENDING_OK, false)
        if (!ok) {
            val tag = prefs.getString(AppCorePrefs.KEY_BOOT_PENDING_SOURCE, null)
            val keys = if (tag == "ota" || tag == "bundle") {
                listOf(tag)
            } else {
                listOf("ota", "bundle") // نشانگر قدیمی/ناشناخته — محافظه‌کار
            }
            val edit = prefs.edit()
            for (t in keys) {
                val key = AppCorePrefs.failKey(pendingRev, t)
                edit.putInt(key, prefs.getInt(key, 0) + 1)
            }
            edit.putInt(AppCorePrefs.KEY_LAST_FAIL_REV, pendingRev)
                .putString(
                    AppCorePrefs.KEY_LAST_FAIL_MSG,
                    "پروسه در رندر rev$pendingRev مُرد (بووت قبلی بدون اولین فریم موفق)",
                )
                .apply()
            Log.w(TAG, "بووت قبلی پیش از اولین فریم موفق مُرد — rev$pendingRev یک شکست ثبت شد")
        }
        prefs.edit().putInt(AppCorePrefs.KEY_BOOT_PENDING_REV, 0).apply()
    }

    /** لود — روی IO صدا زده شود (دیسک + بازکردن کلاس‌ها) */
    fun load(context: Context): Result {
        val appContext = context.applicationContext
        val bodyCode = AppCoreIo.bodyVersionCode(appContext)
        val prefs = appContext.getSharedPreferences(AppCorePrefs.FILE, Context.MODE_PRIVATE)
        settlePendingBoot(prefs)

        data class Candidate(val dir: File, val manifest: AppCoreManifest, val source: String, val tag: String)

        val candidates = mutableListOf<Candidate>()
        // ۱) هسته‌های OTA — پوشه‌های عددیِ filesDir/core/versions
        runCatching {
            val versions = File(appContext.filesDir, "core/versions")
            versions.listFiles { f -> f.isDirectory && f.name.toIntOrNull() != null }
                ?.sortedByDescending { it.name.toInt() }
                ?.forEach { dir ->
                    parseManifestIn(dir)?.let { candidates.add(Candidate(dir, it, "کانال انتشار", "ota")) }
                }
        }
        // ۲) باندل APK — asset به کش کپی می‌شود (DexClassLoader مسیر واقعی می‌خواهد)
        runCatching {
            bundledDir(appContext)?.let { dir ->
                parseManifestIn(dir)?.let { candidates.add(Candidate(dir, it, "باندل APK", "bundle")) }
            }
        }

        candidates.sortByDescending { it.manifest.revision }
        var bestReason = "هیچ بستهٔ هسته‌ای پیدا نشد"

        for (c in candidates) {
            val m = c.manifest
            val fails = prefs.getInt(AppCorePrefs.failKey(m.revision, c.tag), 0)
            if (fails >= AppCorePrefs.MAX_FAILS) {
                val lastMsg = prefs.getString(AppCorePrefs.KEY_LAST_FAIL_MSG, "") ?: ""
                bestReason = "هستهٔ rev${m.revision} ${AppCorePrefs.MAX_FAILS} بار پیاپی شکست خورد — کنار گذاشته شد" +
                    (if (lastMsg.isNotBlank()) "؛ آخرین خطا: $lastMsg" else "")
                Log.w(TAG, bestReason)
                continue
            }
            if (m.minBodyVersionCode > bodyCode) {
                bestReason = "هستهٔ rev${m.revision} بدنهٔ تازه‌تر می‌خواهد (min ${m.minBodyVersionCode} > $bodyCode)"
                Log.w(TAG, bestReason)
                continue
            }
            try {
                val dex = File(c.dir, AppCoreManifest.DEX_NAME)
                if (!dex.isFile) throw IllegalStateException("classes.dex پیدا نشد")
                val actual = AppCoreIo.sha256File(dex)
                if (!actual.equals(m.dexSha256, ignoreCase = true)) {
                    throw IllegalStateException("sha256 هسته با مانیفست بسته نمی‌خواند")
                }
                val optDir = File(appContext.codeCacheDir, "core-opt-${m.revision}").apply { mkdirs() }
                val loader = DexClassLoader(
                    dex.absolutePath, optDir.absolutePath, null, appContext.classLoader,
                )
                val cls = Class.forName(m.entryClass, true, loader)
                val entry = cls.getDeclaredConstructor().newInstance() as AppCoreEntry
                // موفق — وضعیت فعال برای کارت «هستهٔ اپ» ثبت می‌شود
                prefs.edit()
                    .putInt(AppCorePrefs.KEY_ACTIVE_REVISION, m.revision)
                    .putString(AppCorePrefs.KEY_ACTIVE_SOURCE, c.source)
                    .putString(AppCorePrefs.KEY_ACTIVE_VERSION_NAME, m.versionName)
                    .putInt(AppCorePrefs.failKey(m.revision, c.tag), 0)
                    .apply()
                Log.i(TAG, "هسته لود شد: rev${m.revision} از «${c.source}»")
                return Result.Loaded(entry, m.revision, c.source, c.tag)
            } catch (t: Throwable) {
                prefs.edit()
                    .putInt(AppCorePrefs.failKey(m.revision, c.tag), fails + 1)
                    .putInt(AppCorePrefs.KEY_LAST_FAIL_REV, m.revision)
                    .putString(
                        AppCorePrefs.KEY_LAST_FAIL_MSG,
                        "${c.source}: ${t.javaClass.name}: ${t.message ?: "بدون پیام"}".take(240),
                    )
                    .apply()
                bestReason = "rev${m.revision} (${c.source}): ${t.message ?: t.javaClass.simpleName}"
                Log.w(TAG, "لود هسته شکست خورد — $bestReason")
            }
        }
        return Result.Failed(bestReason)
    }

    /** بازکردن یک دایرکتوری نسخه → پارس مانیفست؛ هر ایراد = null */
    private fun parseManifestIn(dir: File): AppCoreManifest? = try {
        val f = File(dir, AppCoreManifest.MANIFEST_NAME)
        if (!f.isFile) null else AppCoreManifest.parse(JSONObject(f.readText()))
    } catch (e: Exception) {
        null
    }

    /**
     * باندل assets/core/core-bundle.zip → کش. کش با طول+هشِ بسته کلید می‌خورد
     * تا هر APK جدید یک بار باز شود؛ خروجی، دایرکتوریِ مانیفست+dex.
     */
    private fun bundledDir(context: Context): File? = try {
        val bytes = context.assets.open(AppCoreManifest.BUNDLE_ASSET).use { it.readBytes() }
        val key = "%x".format(bytes.size) + "-" + AppCoreIo.sha256Bytes(bytes).take(8)
        val dir = File(File(context.cacheDir, "bundled-core"), key)
        if (File(dir, AppCoreManifest.DEX_NAME).isFile) {
            dir
        } else {
            dir.deleteRecursively()
            dir.mkdirs()
            val zip = File(dir, "bundle.zip")
            zip.writeBytes(bytes)
            AppCoreIo.unzip(zip, dir)
            zip.delete()
            dir
        }
    } catch (e: Exception) {
        // asset نبود (بیلد بدون گام pack) یا zip بد — فالبک به کاندیدای بعدی
        null
    }
}

/**
 * صفحهٔ صادقانهٔ بدنه وقتی هیچ هسته‌ای لود نشد — فیک نمی‌زند؛ دلیل فنی را
 * می‌گوید و راه بازگشت را نشان می‌دهد. با بسته/باز شدن اپ، لودر دوباره
 * کاندیداها را می‌سنجد و هستهٔ جاافتاده خودکار فالبک می‌شود.
 */
@Composable
fun CoreFatalScreen(reason: String) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SwallowBadge(size = 116.dp)
            Spacer(Modifier.height(22.dp))
            Text("بدنه بالا آمد؛ هسته لود نشد", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))
            Text(
                "معماری پرستو موبایل دو تکه است: بدنهٔ ثابت (این APK) و هستهٔ رابط و منطق که بی‌نصب از کانال انتشار تازه می‌شود. الان هیچ نسخه‌ای از هسته سالم لود نشد — دلیل:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Card(Modifier.fillMaxSize(), shape = RoundedCornerShape(14.dp)) {
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(14.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "اپ را ببندید و دوباره باز کنید؛ اگر هستهٔ OTA خراب باشد بدنه خودش به باندل داخل APK برمی‌گردد. اگر تازه بدنه را آپدیت کرده‌اید و این پیام می‌ماند، بستهٔ انتشار ناقص است — کانال انتشار را بررسی کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
