package network.selfevolving.parastoo.mobile.core.provision

import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

/**
 * گرفتن آخرین بستهٔ پل (access-agent) مستقیماً از کانال انتشار گیت‌هاب.
 * قرارداد: docs/AGENT_PROTOCOL.fa.md §۸ (پل v0.5.2 به بعد).
 *
 * صحت دو-کاناله: مانیفست (نسخه + sha256) از سورس main مخزن می‌آید و فایل باندل
 * از دارایی رلیز — دو مسیر مستقل. هر مسیر اول مستقیم و بعد با پیش‌وند gh-proxy
 * امتحان می‌شود (واقعیت شبکهٔ ایران). اگر sha256 فایل با مانیفست نخواند،
 * دانلود رد قطعی می‌شود — تزریق از آینه/پروکسی hostile بسته است.
 */
object BridgeReleaseClient {

    data class BridgeRelease(
        val version: String,
        val filename: String,
        val sha256: String,
        val bytes: ByteArray,
        val source: String, // توضیح فارسی مسیر واقعی — برای پیام صادقانه به ادمین
    )

    class ReleaseException(message: String) : Exception(message)

    private const val ORG = "SelfEvolvingNetwork"
    private const val REPO = "access-agent"
    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/$ORG/$REPO/main/RELEASE_MANIFEST.json"
    private const val ASSET_BASE =
        "https://github.com/$ORG/$REPO/releases/latest/download/"
    private const val PROXY = "https://gh-proxy.com/"
    private const val TIMEOUT_MS = 20_000

    /**
     * آخرین رلیز پل را با راستی‌آزمایی دو-کاناله برمی‌گرداند؛ اگر هیچ مسیری باز
     * نشد ReleaseException — تصمیم فالبک با فراخواننده است (سرور نو: باندل
     * جاسازی‌شده؛ به‌روزرسانی: رد با پیام صادقانه).
     */
    fun fetchLatest(onLog: (String) -> Unit = {}): BridgeRelease {
        // ---------- کانال اول: مانیفست از سورس main ----------
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
        val version = m.optString("version")
        val filename = m.optString("filename")
        val sha = m.optString("sha256")
        if (version.isEmpty() || filename.isEmpty() || sha.isEmpty()) {
            throw ReleaseException("مانیفست انتشار ناقص است")
        }

        // ---------- کانال دوم: باندل از دارایی رلیز ----------
        var lastError: Exception? = null
        for (base in listOf("", PROXY)) {
            val label = if (base.isEmpty()) "مستقیم" else "gh-proxy"
            try {
                val bytes = httpGetBytes(base + ASSET_BASE + filename)
                val actual = sha256Hex(bytes)
                if (!actual.equals(sha, ignoreCase = true)) {
                    throw ReleaseException("sha256 باندل با مانیفست نمی‌خواند — بسته رد شد")
                }
                val source = "$label · مانیفست از $manifestSource"
                onLog("باندل v$version سالم است (${bytes.size} بایت — $source)")
                return BridgeRelease(version, filename, sha, bytes, source)
            } catch (e: Exception) {
                lastError = e
                onLog("باندل از «$label» نیامد: ${e.message}")
            }
        }
        throw ReleaseException("باندل پل از هیچ مسیری گرفته نشد: ${lastError?.message ?: "نامشخص"}")
    }

    private fun httpGet(url: String): String = String(httpGetBytes(url), Charsets.UTF_8)

    private fun httpGetBytes(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.instanceFollowRedirects = true
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw ReleaseException("HTTP $code")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /** مقایسهٔ نسخهٔ سمانتیک ساده («۱.۲.۳») — true یعنی candidate >= current */
    fun isAtLeast(candidate: String, current: String): Boolean {
        val c = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val cur = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(c.size, cur.size)) {
            val a = c.getOrElse(i) { 0 }
            val b = cur.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return true
    }
}
