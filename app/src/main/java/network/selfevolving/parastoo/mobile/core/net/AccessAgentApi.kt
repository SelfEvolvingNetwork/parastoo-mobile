package network.selfevolving.parastoo.mobile.core.net

import network.selfevolving.parastoo.mobile.core.model.AgentTokenMeta
import network.selfevolving.parastoo.mobile.core.model.AuditEvent
import network.selfevolving.parastoo.mobile.core.model.HostStatus
import network.selfevolving.parastoo.mobile.core.model.LifecycleStatus
import network.selfevolving.parastoo.mobile.core.model.SetupTokenInfo
import network.selfevolving.parastoo.mobile.core.security.PinnedTrust
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * کلاینت access-agent — فقط HTTPS پین‌شده + توکن ادمین.
 * قرارداد کامل: docs/AGENT_PROTOCOL.fa.md (§۳ و §۵.۵).
 */
class AccessAgentApi(
    baseUrl: String,
    certSha256: String,
    private val adminToken: String,
) {
    private val root = baseUrl.trim().trimEnd('/')
    private val client: OkHttpClient = PinnedTrust.buildClient(root, certSha256)

    class ApiException(val code: Int, message: String) : Exception(message)
    class PinException(message: String) : Exception(message)

    private fun call(builder: Request.Builder): JSONObject {
        val req = builder
            .header("Authorization", "Bearer $adminToken")
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                JSONObject()
            }
            if (!resp.isSuccessful) {
                throw ApiException(resp.code, json.optString("error", text.take(200)))
            }
            return json
        }
    }

    private fun get(path: String): JSONObject = call(Request.Builder().url(root + path).get())
    private fun post(path: String, body: JSONObject?): JSONObject =
        call(
            Request.Builder().url(root + path)
                .post((body?.toString() ?: "{}").toRequestBody("application/json; charset=utf-8".toMediaType()))
        )

    /** سلامت سبک — عمومی؛ برای تست اولیهٔ پین بدون افشای توکن */
    fun healthz(): JSONObject = call(Request.Builder().url("$root/healthz").get())

    fun status(): HostStatus = HostStatus.from(get("/api/status"))

    fun auditTail(n: Int): List<AuditEvent> {
        val obj = get("/api/admin/audit?tail=$n")
        val lines = obj.optJSONArray("lines") ?: return emptyList()
        return (0 until lines.length()).mapNotNull { AuditEvent.from(lines.optString(it)) }
    }

    fun agentTokenMeta(): AgentTokenMeta = AgentTokenMeta.from(get("/api/admin/agent-token"))

    /** توکن عامل جدید — فقط همین یک بار در پاسخ می‌آید؛ اپ آن را در گاوصندوق نگه می‌دارد */
    fun rotateAgentToken(label: String): String {
        val obj = post("/api/admin/rotate-agent-token", JSONObject().put("label", label))
        return obj.optString("agent_token")
    }

    fun revokeAgentToken() {
        post("/api/admin/revoke-agent-token", null)
    }

    /** کلید توقف قلمرو عامل — true=توقف، false=ادامه */
    fun pauseRealm(paused: Boolean, reason: String) {
        post(
            "/api/admin/agent-realm/pause",
            JSONObject().put("paused", paused).put("reason", reason),
        )
    }

    /** بلوک تحویل آمادهٔ کپی برای عامل هوش مصنوعی */
    fun handoff(): String = get("/api/admin/handoff").optString("block")

    /** وضعیت چرخهٔ عمر پرستو: نصب‌بودن، عملیات جاری، تهٔ لاگ (پل v0.5) */
    fun parastooLifecycle(): LifecycleStatus =
        LifecycleStatus.from(get("/api/admin/parastoo/lifecycle-status"))

    /**
     * توکن نصب سایت برای پایان جادوگر پنل از همین گوشی (پل v0.5.3؛ فرمان مالک
     * 2026-11-09: «توکن نصب سایت باید در پرستو موبایل نمایش داده شود») —
     * کاربر دیگر برای دیدن این توکن به سرور نمی‌رود. فقط خواندن از کانال
     * TLS پین‌شده؛ توکن در لاگ یا گاوصندوق ذخیره نمی‌شود و هر بار تازه خوانده می‌شود.
     */
    fun setupToken(): SetupTokenInfo =
        SetupTokenInfo.from(get("/api/admin/parastoo/setup-token"))

    /**
     * ذخیرهٔ توکن گیت‌هاب مالک روی سرور (فرمان مالک، 2026-11-09): اپ توکن را
     * (اختیاری) از مالک می‌گیرد و فقط یک بار از این مسیر TLS پین‌شده به پل
     * می‌دهد؛ پل آن را در github.env با دسترسی 600 نگه می‌دارد و از آن پس
     * عامل هوش مصنوعی توکن موقتِ هدفمند (purpose-gated) می‌گیرد — هرگز این اپ
     * یا عامل، «پات مالک» را ذخیرهٔ پایدار نمی‌کنند. پاسخ شامل validation است
     * (اعتبارسنجی بهترین‌تلاشی از سمت سرور؛ آفلاین هم ذخیره می‌شود).
     * خودِ توکن هرگز در لاگ اپ یا پل نمی‌آید.
     */
    fun setGithubToken(token: String): JSONObject =
        post("/api/admin/set-github-token", JSONObject().put("token", token))

    /** اجرای یک عملیات چرخهٔ عمر؛ عملیات تخریبی، نام عملیات را به‌عنوان confirm می‌خواهند */
    fun parastooLifecycleAction(action: String): JSONObject =
        post(
            "/api/admin/parastoo/$action",
            JSONObject().put("confirm", action),
        )

    companion object {
        /** نرمال‌سازی ورودی URL ادمین: «193.151.142.128:31563» یا با https هر دو قبول */
        fun normalizeUrl(input: String): String {
            val s = input.trim()
            return when {
                s.startsWith("https://") -> s.trimEnd('/')
                s.startsWith("http://") -> throw IllegalArgumentException("فقط HTTPS مجاز است (پل بدون TLS پذیرفته نمی‌شود)")
                else -> "https://${s.trimEnd('/')}"
            }
        }
    }
}
