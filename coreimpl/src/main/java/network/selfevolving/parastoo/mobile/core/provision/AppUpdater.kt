package network.selfevolving.parastoo.mobile.core.provision

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * به‌روزرسانی خودکار خودِ اپ — حلقهٔ آخرِ آپدیت‌ها (پس از پل و پرستو؛ فرمان
 * مالک 2026-11-09: «پیاده کن»).
 *
 * جریان: باز شدن داشبورد = بررسی کانال انتشار خودِ اپ (ریلیزهای mobile-rN با
 * صحت دو-کاناله) → نسخهٔ تازه‌تر؟ دانلود جریانی + sha256 → آمادهٔ نصب.
 * نصب با تنها یک تأیید سیستم اندروید: نصبِ کاملاً بی‌صدا برای اپِ خارج از
 * استور ممنوعیت امنیتی اندروید است و این نزدیک‌ترین معادل امن به آن است.
 * گارد downgrade: هرگز versionCode کوچک‌تر یا مساوی نسخهٔ جاری نصب نمی‌شود.
 * امضای پایدار CI نصبِ مستقیم روی نسخهٔ قبلی را ممکن می‌کند.
 *
 * این شیء هیچ ارتباطی با سرور/پل ندارد — کاملاً بین گوشی و گیت‌هاب.
 */
object AppUpdater {

    sealed class State {
        /** هنوز بررسی نشده */
        data object Idle : State()

        /** در حال مقایسهٔ نسخه با کانال انتشار */
        data class Checking(val currentName: String, val currentCode: Long) : State()

        /** اپ همان آخرین نسخهٔ کانال را دارد */
        data class UpToDate(val currentName: String, val currentCode: Long) : State()

        /** نسخهٔ تازه‌تر پیدا شد — منتظر فرمان دانلود */
        data class Available(val release: AppReleaseClient.AppRelease) : State()

        /** در حال دانلود جریانی بستهٔ تازه */
        data class Downloading(val release: AppReleaseClient.AppRelease, val bytesRead: Long) : State()

        /** بسته سالم دانلود و راستی‌آزمایی شد — آمادهٔ نصب */
        data class Ready(val release: AppReleaseClient.AppRelease, val apk: File) : State()

        /** بسته آماده است ولی اجازهٔ «نصب از منابع ناشناس» هنوز داده نشده */
        data class NeedsInstallPermission(val release: AppReleaseClient.AppRelease, val apk: File) : State()

        /** ناموفق — شبکه یا مانیفست؛ با «تلاش دوباره» از نو */
        data class Failed(val message: String) : State()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val inFlight = AtomicBoolean(false)
    private var lastCheckMs: Long = 0L

    /** نسخهٔ جاری اپ — مستقیم از PackageManager (حقیقت نصب‌شده، بدون BuildConfig) */
    fun currentVersionCode(context: Context): Long = try {
        val pm = context.packageManager
        val pi = pm.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode
        else @Suppress("DEPRECATION") pi.versionCode.toLong()
    } catch (e: Exception) {
        0L
    }

    fun currentVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    /** مقصد دانلود — حافظهٔ موقت اپ؛ با پاک‌شدن اپ/کش خودبه‌خود می‌رود */
    fun apkCacheFile(context: Context): File =
        File(File(context.cacheDir, "app-update"), "parastoo-mobile-debug.apk")

    /**
     * بررسی کانال انتشار — خودکار با هر باز شدن داشبورد (حداقل فاصله ۱۲۰
     * ثانیه؛ چند سرور = چند فراخوانی، یکی می‌شوند) و دستی با force=true.
     */
    fun check(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheckMs < 120_000) return
        if (!inFlight.compareAndSet(false, true)) return
        lastCheckMs = now
        scope.launch {
            try {
                val currentCode = currentVersionCode(context)
                val currentName = currentVersionName(context)
                _state.value = State.Checking(currentName, currentCode)
                val rel = AppReleaseClient.fetchManifest()
                if (rel.versionCode <= currentCode) {
                    apkCacheFile(context).delete() // بستهٔ نیمه‌کارهٔ قبلی — دیگر لازم نیست
                    _state.value = State.UpToDate(currentName, currentCode)
                } else {
                    _state.value = State.Available(rel)
                }
            } catch (e: Exception) {
                // شکست شبکه خطای منطقی نیست — کارت همان را صادقانه نشان می‌دهد
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            } finally {
                inFlight.set(false)
            }
        }
    }

    /** دانلود بستهٔ تازه + راستی‌آزمایی sha256 دو-کاناله */
    fun download(context: Context) {
        val rel = (_state.value as? State.Available)?.release ?: return
        if (!inFlight.compareAndSet(false, true)) return
        scope.launch {
            try {
                val dest = apkCacheFile(context)
                dest.delete() // بستهٔ ناقص قبلی
                _state.value = State.Downloading(rel, 0L)
                AppReleaseClient.downloadApk(rel.filename, dest, rel.sha256) { bytes ->
                    _state.value = State.Downloading(rel, bytes)
                }
                _state.value = if (canInstall(context)) State.Ready(rel, dest)
                else State.NeedsInstallPermission(rel, dest)
            } catch (e: Exception) {
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            } finally {
                inFlight.set(false)
            }
        }
    }

    /** آیا اندروید به این اپ اجازهٔ نصب داده است؟ (API 26+ — minSdk 26) */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** باز کردن صفحهٔ اجازهٔ «نصب از منابع ناشناس» برای همین اپ — یک بار برای همیشه */
    fun requestInstallPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * نصب — همان یک تأیید سیستم اندروید. بسته پیش از این مرحله sha256-دو-کاناله
     * شده و امضای پایدار CI نصب روی نسخهٔ قبلی را ممکن می‌کند (امضای متفاوت =
     * خود اندروید نصب را رد می‌کند).
     */
    fun install(context: Context) {
        val st = _state.value
        val apk = when (st) {
            is State.Ready -> st.apk
            is State.NeedsInstallPermission -> st.apk
            else -> return
        }
        if (!apk.exists()) {
            _state.value = State.Failed("بستهٔ دانلودشده پیدا نشد — دوباره دانلود کنید")
            return
        }
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }
}
