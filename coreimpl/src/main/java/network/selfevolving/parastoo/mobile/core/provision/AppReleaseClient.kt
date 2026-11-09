package network.selfevolving.parastoo.mobile.core.provision

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import org.json.JSONObject

/**
 * گرفتن آخرین بستهٔ خودِ اپ (parastoo-mobile) از کانال انتشار گیت‌هاب.
 * قرارداد: همان «صحت دو-کانالهٔ» پل (الگوی BridgeReleaseClient):
 *  کانال اول — RELEASE_MANIFEST.json روی سورس main مخزن اپ
 *             (versionName + versionCode + filename + sha256)
 *  کانال دوم — خود APK از دارایی رلیز گیت‌هاب
 * هر دو مسیر اول مستقیم و بعد با پیش‌وند gh-proxy امتحان می‌شوند (واقعیت
 * شبکهٔ ایران). اگر sha256 فایل دانلودشده با مانیفست نخواند، بسته رد قطعی
 * می‌شود — تزریق از آینه/پروکسی hostile بسته است.
 */
object AppReleaseClient {

    data class AppRelease(
        val versionName: String,
        val versionCode: Long,
        val filename: String,
        val sha256: String,
        val manifestSource: String, // توضیح فارسی مسیر مانیفست — برای پیام صادقانه
    )

    class ReleaseException(message: String) : Exception(message)

    private const val ORG = "SelfEvolvingNetwork"
    private const val REPO = "parastoo-mobile"
    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/$ORG/$REPO/main/RELEASE_MANIFEST.json"
    private const val ASSET_BASE =
        "https://github.com/$ORG/$REPO/releases/latest/download/"
    private const val PROXY = "https://gh-proxy.com/"
    private const val TIMEOUT_MS = 20_000

    /**
     * مانیفست آخرین رلیز اپ — اگر هیچ مسیری باز نشد ReleaseException؛
     * تصمیم فالبک با فراخواننده است (بررسیِ نسخه خطای شبکه، خطای منطقی نیست).
     */
    fun fetchManifest(onLog: (String) -> Unit = {}): AppRelease {
        var manifest: JSONObject? = null
        var manifestSource = ""
        for (base in listOf("", PROXY)) {
            val label = if (base.isEmpty()) "مستقیم" else "gh-proxy"
            try {
                manifest = JSONObject(httpGet(base + MANIFEST_URL))
                manifestSource = label
                break
            } catch (e: Exception) {
                onLog("مانیفست از «$label» نیامد: ${e.message}")
            }
        }
        val m = manifest ?: throw ReleaseException("مانیفست انتشار از هیچ مسیری گرفته نشد")
        val name = m.optString("versionName")
        val code = m.optLong("versionCode", 0L)
        val filename = m.optString("filename")
        val sha = m.optString("sha256")
        if (name.isEmpty() || code <= 0L || filename.isEmpty() || sha.isEmpty()) {
            throw ReleaseException("مانیفست انتشار ناقص است")
        }
        return AppRelease(name, code, filename, sha, manifestSource)
    }

    /**
     * بخش «core» مانیفست انتشار — بستهٔ هستهٔ اپ (کدِ OTA بی‌نصب).
     * قرارداد: RELEASE_MANIFEST.json علاوه بر فیلدهای APK، بخش اختیاری «core»
     * دارد (schema: parastoo-core/1). اگر بخش نبود (مانیفست کهنه) null
     * برمی‌گردد — آپدیتر هسته آن را «به‌روز» می‌فهمد، نه خطا.
     */
    fun fetchCoreRelease(
        onLog: (String) -> Unit = {},
    ): CoreRelease? {
        var manifest: JSONObject? = null
        for (base in listOf("", PROXY)) {
            try {
                manifest = JSONObject(httpGet(base + MANIFEST_URL))
                break
            } catch (e: Exception) {
                onLog("مانیفست از «${if (base.isEmpty()) "مستقیم" else "gh-proxy"}» نیامد: ${e.message}")
            }
        }
        val m = manifest ?: throw ReleaseException("مانیفست انتشار از هیچ مسیری گرفته نشد")
        if (!m.has("core")) return null
        val c = m.optJSONObject("core") ?: return null
        val rev = c.optInt("revision", 0)
        val vc = c.optInt("version_code", 0)
        val vn = c.optString("version_name")
        val minBody = c.optInt("min_body_version_code", 0)
        val filename = c.optString("filename")
        val sha = c.optString("sha256")
        if (rev <= 0 || vc <= 0 || filename.isEmpty() || sha.length != 64) {
            throw ReleaseException("بخش core مانیفست انتشار ناقص است")
        }
        return CoreRelease(rev, vc, vn, minBody, filename, sha)
    }

    /** نسخهٔ هسته از مانیفست انتشار — قرارداد نسخهٔ کدِ OTA */
    data class CoreRelease(
        val revision: Int,
        val versionCode: Int,
        val versionName: String,
        val minBodyVersionCode: Int,
        val filename: String,
        val sha256: String,
    )

    /**
     * دانلود جریانی APK به فایل مقصد با محاسبهٔ sha256 در حین دانلود —
     * بسته چند ده مگابایت است و هرگز کامل در رم نگه داشته نمی‌شود.
     * اگر sha256 با مانیفست نخواند، فایل حذف و ReleaseException پرتاب می‌شود.
     */
    fun downloadApk(
        filename: String,
        dest: File,
        expectedSha256: String,
        onProgress: (bytesRead: Long) -> Unit = {},
    ): File {
        var lastError: Exception? = null
        for (base in listOf("", PROXY)) {
            try {
                dest.parentFile?.mkdirs()
                val conn = URL(base + ASSET_BASE + filename).openConnection() as HttpURLConnection
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = 60_000
                conn.instanceFollowRedirects = true
                try {
                    if (conn.responseCode !in 200..299) {
                        throw ReleaseException("HTTP ${conn.responseCode}")
                    }
                    val digest = MessageDigest.getInstance("SHA-256")
                    var read = 0L
                    dest.outputStream().use { out ->
                        DigestInputStream(conn.inputStream, digest).use { din ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = din.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                read += n
                                onProgress(read)
                            }
                        }
                    }
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!actual.equals(expectedSha256, ignoreCase = true)) {
                        dest.delete()
                        throw ReleaseException("sha256 بسته با مانیفست نمی‌خواند — بسته رد شد")
                    }
                    return dest
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                lastError = e
                dest.delete()
            }
        }
        throw ReleaseException("APK از هیچ مسیری گرفته نشد: ${lastError?.message ?: "نامشخص"}")
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.instanceFollowRedirects = true
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw ReleaseException("HTTP $code")
            return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }
}
