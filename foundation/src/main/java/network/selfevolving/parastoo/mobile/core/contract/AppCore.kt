package network.selfevolving.parastoo.mobile.core.contract

import android.content.Context
import androidx.compose.runtime.Composable
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.json.JSONObject

/**
 * قرارداد بدنه ↔ هسته — تنها مرز رسمی بین APK ثابت و کدِ OTA (نسخهٔ ۱).
 *
 * معماری «بدنه ثابت + هستهٔ قابل تعویض» (فرمان مالک 2026-11-09: «پرستو موبایل
 * دو تکه شود؛ بدنهٔ ثابت یک بار نصب، هستهٔ اصلی توسط بدنه بروز شود — بدون نصب
 * مجدد APK»):
 *
 *  - بدنه (:app) فقط لودر دارد: بستهٔ هسته (classes.dex + manifest.json در یک
 *    zip) از کانال انتشار گیت‌هاب با صحت دو-کاناله می‌آید، با DexClassLoader
 *    (parent-first) لود می‌شود و تنها کلاسِ بدنه/هسته که همدیگر را می‌بینند
 *    همین interface است.
 *  - هسته (:coreimpl) این interface را پیاده می‌کند؛ هر کلاس دیگری که هسته از
 *    بدنه بخواهد باید از طریق :foundation بیاید (گاوصندوق، پین، دیتابیس، پیکربندی).
 *  - تغییر این قرارداد یعنی تغییر نسخهٔ SCHEMA — بدنهٔ کهنه هستهٔ ناسازگار را
 *    رد می‌کند و به باندلِ درون APK برمی‌گردد (rollback خودکار).
 *
 * تم/فونت/حالت راست‌به‌چپ از بدنه می‌آید (MainActivity هسته را داخل
 * ParastooTheme می‌پیچد)؛ گیت قفل گوشی هم کار بدنه است و پیش از رندر هسته.
 */
interface AppCoreEntry {

    /** رندر ریشهٔ اپ — بدنه این را داخل تم و RTL خودش صدا می‌زند */
    @Composable
    fun App()
}

/**
 * نام‌های مشترک وضعیتِ هسته روی گوشی — بدنه (لودر) و هسته (آپدیتر و کارت)
 * هر دو از همین ثابت‌ها استفاده می‌کنند تا هرگز کلیدها از هم جدا نشوند.
 */
object AppCorePrefs {
    const val FILE = "parastoo_core_state"

    /** revision هستهٔ فعلاً فعال (اطلاعاتی — انتخابِ نسخه در لودر، بیشینهٔ سالم است) */
    const val KEY_ACTIVE_REVISION = "active_revision"

    /** منبع هستهٔ فعال: «باندل APK» یا «کانال انتشار» */
    const val KEY_ACTIVE_SOURCE = "active_source"

    /** versionName هستهٔ فعال */
    const val KEY_ACTIVE_VERSION_NAME = "active_version_name"

    /** شمارش بوت ناموفق هر (revision، منبع) — دو شکست پیاپی = کنار گذاشتن و فالبک.
     *  درس rev31: شمارش باید به تفکیک منبع باشد — وگرنه شکستِ نسخهٔ OTA،
     *  همان نسخهٔ سالمِ باندل داخل APK را هم همیشه مسموم می‌کند و بدنه
     *  هرگز نمی‌تواند به فالبک برگردد. */
    fun failKey(revision: Int, source: String) = "fail_count_${source}_$revision"

    /** سقف شکست پیاپی پیش از کنار گذاشتن یک هسته */
    const val MAX_FAILS = 2

    /** آخرین خطای واقعی لود (rev + پیام استثنا) — پیام صادقانهٔ صفحهٔ بدنه */
    const val KEY_LAST_FAIL_REV = "last_fail_rev"
    const val KEY_LAST_FAIL_MSG = "last_fail_msg"

    /** نشانگر سلامت رندر بووتِ جاری — درس rev31: مرگِ پروسه در اولین رندر
     *  هسته (مثلاً NoClassDefFoundError در Compose) نباید بی‌شمارش بماند؛
     *  بووتِ بعدی آن را به شمارش شکست همان (rev، منبع) می‌بُرد. */
    const val KEY_BOOT_PENDING_REV = "boot_pending_rev"
    const val KEY_BOOT_PENDING_SOURCE = "boot_pending_source"
    const val KEY_BOOT_PENDING_OK = "boot_pending_ok"
}

/**
 * ابزارهای مشترک بدنه/هسته — تنها دوطرفه‌ای که هر دو سمت به آن نیاز دارند:
 * نسخهٔ بدنه، باز کردن بستهٔ هسته و هش. در :foundation است تا هیچ‌یک از دو
 * ماژول به کلاس‌های مستقیم دیگری وابسته نشود.
 */
object AppCoreIo {

    /** نسخهٔ بدنه — حقیقت نصب‌شده، بدون BuildConfig */
    fun bodyVersionCode(context: Context): Long = try {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    } catch (e: Exception) {
        0L
    }

    /** باز کردن zip سخت‌گیرانه — فقط دو عضو قراردادی؛ هر عضو دیگری = رد کل بسته */
    fun unzip(zipFile: File, outDir: File) {
        ZipFile(zipFile).use { zf ->
            val allowed = setOf(AppCoreManifest.MANIFEST_NAME, AppCoreManifest.DEX_NAME)
            val names = zf.entries().asSequence().map { it.name }.toSet()
            if (!names.containsAll(allowed)) {
                throw IllegalStateException("بستهٔ هسته ناقص است: ${names.joinToString()}")
            }
            zf.entries().asSequence().forEach { entry ->
                if (entry.name !in allowed) {
                    throw IllegalStateException("عضو غیرقراردادی در بستهٔ هسته: ${entry.name}")
                }
                if (entry.isDirectory) return@forEach
                val out = File(outDir, entry.name)
                zf.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
            }
        }
    }

    /** sha256 جریانی فایل — بستهٔ هسته هرگز کامل در رم نمی‌ماند */
    fun sha256File(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256Bytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}

/**
 * مانیفست بستهٔ هسته — قرارداد مشترکِ اسکریپت pack (CI)، لودر بدنه و آپدیتر هسته.
 * بستهٔ zip دقیقاً دو عضو دارد: [MANIFEST_NAME] و [DEX_NAME].
 */
data class AppCoreManifest(
    val schema: String,
    val revision: Int,
    val versionCode: Int,
    val versionName: String,
    val minBodyVersionCode: Int,
    val entryClass: String,
    val dexSha256: String,
) {

    fun isValid(): Boolean =
        schema == SCHEMA && revision > 0 && versionCode > 0 &&
            minBodyVersionCode > 0 && entryClass.isNotBlank() && dexSha256.length == 64

    companion object {
        const val SCHEMA = "parastoo-core-bundle/1"
        const val DEX_NAME = "classes.dex"
        const val MANIFEST_NAME = "manifest.json"

        /** مسیر بستهٔ جاسازی‌شده در assets بدنه (فایل .gitignore است — CI می‌سازد) */
        const val BUNDLE_ASSET = "core/core-bundle.zip"

        /** نام دارایی رلیز گیت‌هاب — ثابت تا «releases/latest/download/» همیشه کار کند */
        const val RELEASE_ASSET = "parastoo-core.zip"

        /**
         * پارس سخت‌گیرانه — هر فیلد نامعتبر = رد کل بسته (پارس null).
         * لودر بعد از این پارس، sha256 خود classes.dex را هم با [dexSha256]
         * می‌سنجد؛ یعنی دو لایه: هشِ داخل بسته + هشِ کانال دوم (مانیفست رلیز).
         */
        fun parse(json: JSONObject): AppCoreManifest? = try {
            val m = AppCoreManifest(
                schema = json.optString("schema"),
                revision = json.optInt("revision", -1),
                versionCode = json.optInt("version_code", -1),
                versionName = json.optString("version_name"),
                minBodyVersionCode = json.optInt("min_body_version_code", -1),
                entryClass = json.optString("entry_class"),
                dexSha256 = json.optString("dex_sha256"),
            )
            if (m.isValid()) m else null
        } catch (e: Exception) {
            null
        }
    }
}
