package network.selfevolving.parastoo.mobile.core.config

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * انبار هستهٔ هات — تک‌نمونه؛ همهٔ صفحه‌ها از همین می‌خوانند.
 *
 * ترتیب منابع: نسخهٔ باندل‌شدهٔ داخل APK (خط پایینِ اطمینان) → کش معتبرِ محلی
 * (فقط revision بالاتر) → واکشی بی‌سروصدا از پل در داشبورد. هر خطا = ادامه با
 * نسخهٔ فعلی؛ اپ بدون این کانال هم کامل کار می‌کند (پل قدیمی 404 می‌دهد و همین
 * عادی است). فلگ‌ها و endpointها به‌صورت زنده اعمال می‌شوند — state کامپوز.
 */
object CoreConfigStore {

    /** وضعیت جاری — state کامپوز؛ صفحه‌ها با تغییرش خودکار تازه می‌شوند */
    var state: CoreConfig by mutableStateOf(CoreConfig(0, 0, emptyMap(), emptyMap(), emptyMap()))
        private set

    /** آخرین یادداشت واکشی — برای نمایش اختیاری؛ هرگز راز ندارد */
    var lastNote: String by mutableStateOf("")
        private set

    private var appContext: Context? = null
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        // ۱) نسخهٔ باندل‌شده — اگر به هر دلیل خوانده نشد، نقشه‌های خالی می‌مانند
        // و همهٔ فالبک‌های کامپایل‌شدهٔ call-siteها فعال می‌شوند (اپ سالم می‌ماند).
        try {
            val text = appContext!!.assets.open("core/parastoo-core.json")
                .bufferedReader().use { it.readText() }
            CoreConfig.parse(JSONObject(text))?.let { state = it }
        } catch (e: Exception) {
            lastNote = "هستهٔ هات: asset نخوانده شد — فالبک کامپایل‌شده"
        }
        // ۲) کش محلیِ واکشی قبلی — فقط اگر از باندل تازه‌تر است
        readCache()?.let { cached ->
            if (cached.revision > state.revision) state = cached
        }
    }

    fun get(): CoreConfig = state

    /**
     * واکشی بی‌سروصدا از پل — روی IO؛ فقط revision بزرگ‌تر اعمال می‌شود؛
     * گارد min_version_code اپِ قدیمی را از پیکربندی ناسازگار محافظت می‌کند.
     * [fetchEnvelope] را جدا نگه داشتیم تا این کلاس به لایهٔ شبکه وابسته
     * نماند (وابستگی یک‌طرفه: net → config).
     */
    suspend fun refresh(fetchEnvelope: suspend () -> JSONObject) = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext
        try {
            val env = fetchEnvelope()
            val revision = env.optInt("revision", -1)
            if (revision <= state.revision) {
                lastNote = "هستهٔ هات: بدون تغییر (revision $revision)"
                return@withContext
            }
            val minCode = env.optInt("min_version_code", 0)
            if (minCode > currentVersionCode(ctx)) {
                lastNote = "هستهٔ هات رد شد: نیاز به اپ تازه‌تر (min $minCode)"
                return@withContext
            }
            val parsed = CoreConfig.parse(env, env.optString("sha256"))
            if (parsed == null) {
                lastNote = "هستهٔ هات رد شد: پاکت نامعتبر یا هش ناهم‌خوان"
                return@withContext
            }
            writeCache(env.toString())
            state = parsed
            lastNote = "هستهٔ هات به revision $revision اعمال شد"
        } catch (e: Exception) {
            // پل قدیمی (404) یا قطعی شبکه — کاملاً عادی؛ ادامه با نسخهٔ فعلی
            lastNote = "هستهٔ هات: واکشی نشد (${e.message ?: e.javaClass.simpleName})"
        }
    }

    private fun cacheFile(ctx: Context): File = File(ctx.filesDir, "core/core.json")

    private fun readCache(): CoreConfig? {
        val ctx = appContext ?: return null
        return try {
            val f = cacheFile(ctx)
            if (!f.exists()) return null
            val env = JSONObject(f.readText())
            CoreConfig.parse(env, env.optString("sha256")) // هش کش هم دوباره سنجیده می‌شود
        } catch (e: Exception) {
            try { cacheFile(ctx).delete() } catch (e2: Exception) { /* بی‌اهمیت */ }
            null
        }
    }

    private fun writeCache(text: String) {
        val ctx = appContext ?: return
        try {
            val f = cacheFile(ctx)
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        } catch (e: Exception) {
            // کش نیمه‌کاره → دفعهٔ بعد readCache رد می‌کند؛ عملکرد اپ دست‌نخورده
        }
    }

    /** نسخهٔ جاری اپ — مستقیم از PackageManager (حقیقت نصب‌شده، بدون BuildConfig) */
    private fun currentVersionCode(ctx: Context): Long = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode
        else @Suppress("DEPRECATION") pi.versionCode.toLong()
    } catch (e: Exception) {
        0L
    }
}
