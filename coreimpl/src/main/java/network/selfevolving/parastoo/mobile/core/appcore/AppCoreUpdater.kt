package network.selfevolving.parastoo.mobile.core.appcore

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import network.selfevolving.parastoo.mobile.core.contract.AppCoreIo
import network.selfevolving.parastoo.mobile.core.contract.AppCoreManifest
import network.selfevolving.parastoo.mobile.core.contract.AppCorePrefs
import network.selfevolving.parastoo.mobile.core.provision.AppReleaseClient
import org.json.JSONObject

/**
 * به‌روزرسان هستهٔ اپ — کانال OTA بی‌نصب (فرمان مالک 2026-11-09:
 * «بدنه ثابت یک بار نصب؛ هستهٔ اصلی توسط بدنه بروز شود؛ نیازمند نصب apk نیست»).
 *
 * جریان کامل بی‌پرسش، مثل حلقهٔ خودکار پل:
 *  ۱) هر شروع اپ، بخش «core» مانیفست انتشار (RELEASE_MANIFEST.json روی main)
 *     با revision فعال روی گوشی مقایسه می‌شود — گارد downgrade سخت.
 *  ۲) نسخهٔ تازه‌تر؟ دانلود جریانی parastoo-core.zip از دارایی رلیز
 *     (مستقیم ← gh-proxy) با sha256 دو-کاناله در حین دانلود.
 *  ۳) بازکردن سخت‌گیرانهٔ zip (فقط manifest.json + classes.dex) + پارس
 *     AppCoreManifest + سنجش sha داخلی classes.dex + گارد سازگاری بدنه.
 *  ۴) نصب اتمیک در filesDir/core/versions/<revision>/ (نوشتن در .tmp سپس rename).
 *  ۵) بازآرایی نرم اکتیویتی (recreate) — لودر بدنه در onCreate بعدی بیشینهٔ
 *     revision سالم را لود می‌کند؛ هیچ دیالوگ اندرویدی در کل مسیر نیست چون
 *     هیچ APK نصب نمی‌شود.
 *
 * سلامت‌سنجی در بدنه است (AppCoreLoader): اگر هستهٔ تازه دو بار پیاپی بوت
 * ناموفق بدهد، بدنه خودش به نسخهٔ قبلی یا باندل داخل APK فالبک می‌کند.
 */
object AppCoreUpdater {

    sealed class State {
        /** هنوز بررسی نشده */
        data object Idle : State()

        /** در حال مقایسهٔ revision با کانال انتشار */
        data class Checking(val activeRevision: Int) : State()

        /** هسته همان آخرین نسخهٔ کانال را دارد */
        data class UpToDate(val activeRevision: Int) : State()

        /** در حال دانلود بستهٔ تازه */
        data class Downloading(val revision: Int, val bytesRead: Long) : State()

        /** بسته سالم نصب شد — بدنه در حال بازآرایی نرم */
        data class Installed(val fromRevision: Int, val toRevision: Int) : State()

        /** ناموفق — پیام صادقانه؛ کانال بعد از ۱۲۰ ثانیه یا با «بررسی دوباره» */
        data class Failed(val message: String) : State()
    }

    private const val TAG = "ParastooCoreUpdater"
    private const val THROTTLE_MS = 120_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val inFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastCheckMs = 0L

    /** revision هستهٔ فعلاً فعال — همان چیزی که لودر بدنه ثبت کرده */
    fun activeRevision(context: Context): Int =
        context.getSharedPreferences(AppCorePrefs.FILE, Context.MODE_PRIVATE)
            .getInt(AppCorePrefs.KEY_ACTIVE_REVISION, 0)

    fun activeSource(context: Context): String =
        context.getSharedPreferences(AppCorePrefs.FILE, Context.MODE_PRIVATE)
            .getString(AppCorePrefs.KEY_ACTIVE_SOURCE, "") ?: ""

    fun activeVersionName(context: Context): String =
        context.getSharedPreferences(AppCorePrefs.FILE, Context.MODE_PRIVATE)
            .getString(AppCorePrefs.KEY_ACTIVE_VERSION_NAME, "") ?: ""

    /**
     * بررسی بی‌صدا — در هر شروع اپ از AppRoot و دستی با force=true از کارت.
     * چند فراخوانی هم‌زمان یکی می‌شوند؛ شکست شبکه خطای منطقی نیست.
     */
    fun check(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheckMs < THROTTLE_MS) return
        if (!inFlight.compareAndSet(false, true)) return
        lastCheckMs = now
        val appContext = context.applicationContext
        scope.launch {
            try {
                val active = activeRevision(appContext)
                _state.value = State.Checking(active)

                // ۱) کانال انتشار — بخش core؛ نبودش یعنی مانیفست کهنه = به‌روز
                val rel = AppReleaseClient.fetchCoreRelease()
                if (rel == null || rel.revision <= active) {
                    _state.value = State.UpToDate(active)
                    return@launch
                }

                // ۲) دانلود جریانی + sha256 کانال دوم در حین دانلود
                _state.value = State.Downloading(rel.revision, 0L)
                val dest = File(File(appContext.cacheDir, "core-update"), AppCoreManifest.RELEASE_ASSET)
                AppReleaseClient.downloadApk(rel.filename, dest, rel.sha256) { bytes ->
                    _state.value = State.Downloading(rel.revision, bytes)
                }

                // ۳) بازکردن سخت‌گیرانه + مانیفست داخلی + هش dex + سازگاری بدنه
                val work = File(File(appContext.cacheDir, "core-update"), "verify-${rel.revision}")
                work.deleteRecursively()
                work.mkdirs()
                AppCoreIo.unzip(dest, work)
                val manifest = AppCoreManifest.parse(
                    JSONObject(File(work, AppCoreManifest.MANIFEST_NAME).readText()),
                ) ?: throw IllegalStateException("مانیفست داخل بستهٔ هسته نامعتبر است")
                if (manifest.revision != rel.revision) {
                    throw IllegalStateException("revision بسته (${manifest.revision}) با مانیفست انتشار (${rel.revision}) نمی‌خواند")
                }
                val bodyCode = AppCoreIo.bodyVersionCode(appContext)
                if (manifest.minBodyVersionCode > bodyCode) {
                    throw IllegalStateException("هستهٔ تازه بدنهٔ تازه‌تر می‌خواهد — اول APK بدنه را از کارت «به‌روزرسانی خودکار اپ» بگیرید")
                }

                // ۴) نصب اتمیک — دایرکتوری نسخه با rename
                val versionDir = File(File(appContext.filesDir, "core/versions"), rel.revision.toString())
                if (versionDir.exists()) versionDir.deleteRecursively()
                versionDir.parentFile?.mkdirs()
                val tmp = File(versionDir.parentFile, ".tmp-${rel.revision}")
                tmp.deleteRecursively()
                tmp.mkdirs()
                File(work, AppCoreManifest.DEX_NAME).renameTo(File(tmp, AppCoreManifest.DEX_NAME)) ||
                    throw IllegalStateException("انتقال classes.dex ناموفق بود")
                File(work, AppCoreManifest.MANIFEST_NAME).renameTo(File(tmp, AppCoreManifest.MANIFEST_NAME)) ||
                    throw IllegalStateException("انتقال مانیفست ناموفق بود")
                if (!tmp.renameTo(versionDir)) {
                    // rename بین‌فرهنگی گاهی شکست می‌خورد — کپی امن
                    tmp.copyRecursively(versionDir, overwrite = true)
                    tmp.deleteRecursively()
                }
                // گارد dex قابل‌نوشتن (درس rev31 — علت واقعی «Writable dex file
                // … is not allowed» روی گوشی مالک): نسخهٔ نصب‌شده باید read-only
                // باشد؛ مسیر کپی امن، پرمیشن read-only استخراج را نمی‌برد
                val installedDex = File(versionDir, AppCoreManifest.DEX_NAME)
                if (installedDex.canWrite() && !(installedDex.setReadOnly() && !installedDex.canWrite())) {
                    throw IllegalStateException("classes.dex نصب‌شده read-only نشد — اندروید ۱۴+ لودش را رد می‌کند")
                }
                work.deleteRecursively()
                dest.delete()

                // ۵) بازآرایی نرم — recreate اکتیویتی؛ لودر هستهٔ تازه را می‌گیرد
                _state.value = State.Installed(active, rel.revision)
                Log.i(TAG, "هستهٔ rev${rel.revision} نصب شد — بازآرایی نرم")
                Handler(Looper.getMainLooper()).postDelayed({
                    (context as? Activity)?.recreate()
                }, 400)
            } catch (e: Exception) {
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            } finally {
                inFlight.set(false)
            }
        }
    }
}
