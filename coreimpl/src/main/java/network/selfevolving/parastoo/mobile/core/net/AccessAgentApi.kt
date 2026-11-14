package network.selfevolving.parastoo.mobile.core.net

import network.selfevolving.parastoo.mobile.core.config.CoreConfigStore
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

    /**
     * مسیر endpoint از هستهٔ هات با فالبک کامپایل‌شده — پل می‌تواند مسیرها را
     * بدون بیلد تازه عوض کند؛ هرگز آدرس/دامنهٔ سرور را عوض نمی‌کند (کانال TLS
     * پین‌شده همیشه با آدرسِ ثبت‌شدهٔ پروفایل ساخته می‌شود).
     */
    private fun p(key: String, fallback: String): String =
        CoreConfigStore.get().ep(key, fallback)

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
    fun healthz(): JSONObject = call(Request.Builder().url(root + p("healthz", "/healthz")).get())

    fun status(): HostStatus = HostStatus.from(get(p("status", "/api/status")))

    fun auditTail(n: Int): List<AuditEvent> {
        val obj = get(p("audit", "/api/admin/audit") + "?tail=$n")
        val lines = obj.optJSONArray("lines") ?: return emptyList()
        return (0 until lines.length()).mapNotNull { AuditEvent.from(lines.optString(it)) }
    }

    fun agentTokenMeta(): AgentTokenMeta = AgentTokenMeta.from(get(p("agent_token", "/api/admin/agent-token")))

    /** توکن عامل جدید — فقط همین یک بار در پاسخ می‌آید؛ اپ آن را در گاوصندوق نگه می‌دارد */
    fun rotateAgentToken(label: String): String {
        val obj = post(p("rotate_agent_token", "/api/admin/rotate-agent-token"), JSONObject().put("label", label))
        return obj.optString("agent_token")
    }

    fun revokeAgentToken() {
        post(p("revoke_agent_token", "/api/admin/revoke-agent-token"), null)
    }

    /** کلید توقف قلمرو عامل — true=توقف، false=ادامه */
    fun pauseRealm(paused: Boolean, reason: String) {
        post(
            p("pause_realm", "/api/admin/agent-realm/pause"),
            JSONObject().put("paused", paused).put("reason", reason),
        )
    }

    /** بلوک تحویل آمادهٔ کپی برای عامل هوش مصنوعی */
    fun handoff(): String = get(p("handoff", "/api/admin/handoff")).optString("block")

    /**
     * تأیید رمز جفت‌سازی عامل (پل v0.7.1→v0.8؛ فرمان مالک 2026-11-12): رمز ۵ رقمی
     * را دیگر خودِ عامل نمی‌سازد — سرور آن را در پاسخ GET آدرس دعوت می‌سازد و
     * عامل فقط برای کاربر چاپش می‌کند؛ کاربر آن را از مسیر امن همین اپ برای
     * مالک می‌فرستد و مالک همین‌جا تأییدش می‌کند تا پل بداند از طرف کدام
     * کاربر است. معادل SSH: sudo access-agent approve <code>.
     * پاسخ v0.8 برچسب invited_by (کاربرِ دعوت‌کننده) را هم می‌دهد.
     * مهلت تأیید از صدور رمز ۱۵ دقیقه است؛ پس از تأیید، عامل تا ۳۰ دقیقه
     * فرصت برداشت توکن موقتش را دارد. خودِ رمز هرگز لاگ نمی‌شود.
     */
    fun approveAgentPairing(code: String): JSONObject =
        post(p("agent_approve", "/api/admin/agent-approve"), JSONObject().put("code", code))

    /** کلید اضطراری مالک (پل v0.7.1): ابطال همهٔ نشست‌های موقت جفت‌سازی عامل‌ها */
    fun revokeAgentSessions(): JSONObject =
        post(p("revoke_agent_sessions", "/api/admin/revoke-agent-sessions"), null)

    /**
     * دعوت‌نامهٔ نقش‌دار (پل v0.10؛ درس میدان ۲۰۲۶-۱۱-۱۴ «دعوت ناقص»): نقش باید
     * صریح باشد — «owner» دعوت مالک (pinv_، قلمرو کامل عامل: اجرای دستور،
     * توکن گیت‌هاب، وضعیت کامل) و «user» دعوت کاربر (uinv_، فقط وضعیت
     * فیلترشده). پیش‌فرض مالک — این اپ ابزار مالک است و معادل «sudo
     * access-agent invite owner». پل نقش نامعتبر/غایب را محافظه‌کارانه «user»
     * می‌گیرد؛ همین قاعده این‌جا هم اعمال می‌شود تا دو طرف یک قاعده باشند
     * (نقش ناشناخته → user). پل‌های قدیمی‌تر از v0.10 پارامتر role را نادیده
     * می‌گیرند و دعوت عامل کامل قدیمی می‌دهند (سازگاری). for= فقط برچسب است
     * (invited_by در گزارش رویداد)؛ نقش را عوض نمی‌کند. مسیر از هستهٔ هات
     * می‌آید؛ هر query احتمالی در آن جدا و بازسازی می‌شود تا هیچ «?..?..»
     * دوبلی ساخته نشود — منبع یگانهٔ نقش، همین کد است.
     */
    fun agentInvite(forUser: String = "", role: String = "owner"): String {
        val base = p("agent_invite", "/api/admin/invite").substringBefore('?')
        val safeRole = when (role.trim().lowercase()) {
            "owner", "user" -> role.trim().lowercase()
            else -> "user" // هم‌قاعده با normalize_invite_role پل — محافظه‌کار
        }
        val params = mutableListOf("role=" + safeRole)
        if (forUser.isNotBlank()) {
            params.add("for=" + java.net.URLEncoder.encode(forUser.trim(), "UTF-8"))
        }
        return get(base + "?" + params.joinToString("&")).optString("invite")
    }

    /** وضعیت چرخهٔ عمر پرستو: نصب‌بودن، عملیات جاری، تهٔ لاگ (پل v0.5) */
    fun parastooLifecycle(): LifecycleStatus =
        LifecycleStatus.from(get(p("parastoo_lifecycle_status", "/api/admin/parastoo/lifecycle-status")))

    /**
     * توکن نصب سایت برای پایان جادوگر پنل از همین گوشی (پل v0.5.3؛ فرمان مالک
     * 2026-11-09: «توکن نصب سایت باید در پرستو موبایل نمایش داده شود») —
     * کاربر دیگر برای دیدن این توکن به سرور نمی‌رود. فقط خواندن از کانال
     * TLS پین‌شده؛ توکن در لاگ یا گاوصندوق ذخیره نمی‌شود و هر بار تازه خوانده می‌شود.
     */
    fun setupToken(): SetupTokenInfo =
        SetupTokenInfo.from(get(p("parastoo_setup_token", "/api/admin/parastoo/setup-token")))

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
        post(p("set_github_token", "/api/admin/set-github-token"), JSONObject().put("token", token))

    /** اجرای یک عملیات چرخهٔ عمر؛ عملیات تخریبی، نام عملیات را به‌عنوان confirm می‌خواهند */
    fun parastooLifecycleAction(action: String): JSONObject =
        post(
            p("parastoo_action", "/api/admin/parastoo/") + action,
            JSONObject().put("confirm", action),
        )

    /**
     * حذف خود پل — آخرین فرمان نقطهٔ صفر (پل v0.5.7؛ فرمان مالک 2026-11-09:
     * «پرستو موبایل دستور پاک کردن پل را می‌دهد»). پل کد، راز‌ها، توکن‌ها،
     * گواهی و audit خودش را برای همیشه پاک می‌کند؛ بعد از این، هیچ دسترسی
     * راه‌دوری باقی نمی‌ماند و پاسخ‌نگرفتن اپ «مسیر موفقیت» است. تنها راه
     * بازگشت: جریان «افزودن سرور» با رمز root — مثل اولین روز.
     */
    fun bridgeUninstall(): JSONObject =
        post(
            p("bridge_uninstall", "/api/admin/bridge/uninstall"),
            JSONObject().put("confirm", "uninstall"),
        )

    /**
     * هستهٔ هات اپ — پاکت داده نسخه‌دار از پل (پل v0.5.8؛ فرمان مالک:
     * «بخشی از اپ بدون نصب مجدد بروز شود»). فقط ادمین؛ فقط داده — اپ هرگز
     * کد از راه دور اجرا نمی‌کند. پل قدیمی 404 می‌دهد و اپ با فالبک باندل
     * کامل کار می‌کند.
     */
    fun coreConfig(): JSONObject = get(p("core_config", "/api/admin/app/core-config"))

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
