package network.selfevolving.parastoo.mobile.core.net

import network.selfevolving.parastoo.mobile.core.model.AuditEvent
import network.selfevolving.parastoo.mobile.core.security.PinnedTrust
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * جریان زندهٔ audit (SSE) — GET /api/admin/audit/stream (v0.3 پل).
 *
 * اصل قرارداد مالک: مانیتورینگ باید «نرم‌افزاری» هندل شود و هیچ باری به عامل
 * نیندازد — این کلاس فقط audit.jsonl سمت پل را می‌خواند؛ عامل از هیچ‌چیز خبر ندارد.
 *
 * اتصال قطع‌شده با cursor (آفست بایت) دقیقاً از همان‌جا ادامه می‌یابد؛ خطاها با
 * backoff کوتاه دوباره وصل می‌شوند.
 */
class SseAuditStream(
    baseUrl: String,
    certSha256: String,
    private val adminToken: String,
) {
    private val root = baseUrl.trim().trimEnd('/')
    private val client: OkHttpClient = PinnedTrust.buildClient(root, certSha256)
        .newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // استریم زنده — بدون تایم‌اوت خواندن
        .build()

    @Volatile
    private var running = false

    @Volatile
    private var currentCall: okhttp3.Call? = null

    /** آفست بایت آخرین رویداد دریافت‌شده — برای ادامهٔ دقیق پس از قطعی */
    @Volatile
    var cursor: Long = -1L
        private set

    fun start(
        backfill: Int,
        onEvent: (AuditEvent) -> Unit,
        onConnected: (Boolean) -> Unit,
    ) {
        if (running) return
        running = true
        Thread {
            var url =
                if (cursor >= 0) "$root/api/admin/audit/stream?cursor=$cursor"
                else "$root/api/admin/audit/stream?backfill=$backfill"
            while (running) {
                onConnected(false)
                try {
                    val req = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $adminToken")
                        .header("Accept", "text/event-stream")
                        .build()
                    val c = client.newCall(req)
                    currentCall = c
                    c.execute().use { resp ->
                        if (!resp.isSuccessful) {
                            throw IOException("HTTP ${resp.code}")
                        }
                        onConnected(true)
                        val src = resp.body?.source() ?: throw IOException("بدنهٔ خالی")
                        while (running) {
                            val line = src.readUtf8Line() ?: break // EOF
                            when {
                                line.startsWith("data:") ->
                                    AuditEvent.from(line.removePrefix("data:").trim())
                                        ?.let(onEvent)
                                line.startsWith("id:") ->
                                    line.removePrefix("id:").trim().toLongOrNull()?.let {
                                        cursor = it
                                    }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!running) break // توقف عمدی — نرمال
                }
                if (!running) break
                if (cursor >= 0) url = "$root/api/admin/audit/stream?cursor=$cursor"
                try {
                    Thread.sleep(2000) // backoff قبل از تلاش مجدد
                } catch (e: InterruptedException) {
                    break
                }
            }
            onConnected(false)
        }.apply { isDaemon = true; name = "sse-audit"; start() }
    }

    fun stop() {
        running = false
        try {
            currentCall?.cancel()
        } catch (e: Exception) {
            // بی‌اهمیت — در حال بستن
        }
    }
}
