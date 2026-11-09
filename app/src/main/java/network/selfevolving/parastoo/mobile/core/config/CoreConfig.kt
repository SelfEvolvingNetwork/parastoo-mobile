package network.selfevolving.parastoo.mobile.core.config

import org.json.JSONObject
import java.security.MessageDigest

/**
 * هستهٔ هات پرستو موبایل — دادهٔ نسخه‌دار از پل (schema: `parastoo-core/1`).
 *
 * معماری «پوسته + هستهٔ هات» (فرمان مالک: «بخشی از اپ بدون نصب مجدد بروز شود
 * و بستر یک بار نصب‌شده بماند»): کدِ امضاشدهٔ APK پوسته است و یک بار نصب
 * می‌ماند؛ این داده از پل (TLS پین‌شده + توکن ادمین) واکشی و اعمال می‌شود.
 * قرارداد کامل: پروتکل پل §۵.۸ و docs/SECURITY.fa.md.
 *
 * فقط داده — اپ هرگز کد از راه دور اجرا نمی‌کند؛ فقط نقشهٔ endpointها،
 * متن‌های allowlist‌شده و فلگ‌ها را با اعتبارسنجی سخت‌گیرانه می‌خواند.
 * هر مقدار نامعتبر → ردِ کل پاکت (پارس null) و ادامه با نسخهٔ فعلی.
 */
class CoreConfig(
    val revision: Int,
    val minVersionCode: Int,
    val endpoints: Map<String, String>,
    val strings: Map<String, String>,
    val flags: Map<String, Boolean>,
) {
    /** مسیر endpoint هات با فالبک کامپایل‌شده — هرگز آدرس سرور را عوض نمی‌کند */
    fun ep(key: String, fallback: String): String =
        endpoints[key]?.takeIf {
            it.startsWith("/") && !it.contains("..") && " " !in it && it.length <= 120
        } ?: fallback

    /** متن هات با فالبک کامپایل‌شده — فقط کلیدهای allowlist اصلاً وارد می‌شوند */
    fun str(key: String, fallback: String): String =
        strings[key]?.takeIf { it.isNotBlank() } ?: fallback

    /** فلگ هات با فالبک کامپایل‌شده — فقط بولی؛ فلگ‌ها فقط سطح را کم می‌کنند */
    fun flag(key: String, fallback: Boolean): Boolean = flags[key] ?: fallback

    companion object {
        const val SCHEMA = "parastoo-core/1"

        /**
         * گارد دومِ سمت اپ — همان allowlist پل: فقط متن‌های توصیفیِ غیرتأییدی
         * هات می‌شوند. کلمهٔ تأیید دولایه، عنوان/برچسب کنش‌های تخریبی و هر متن
         * محافظتیِ دیگر همیشه کامپایل‌شده می‌مانند؛ هات‌سازی نمی‌تواند
         * محافظت‌های حذف را نرم کند.
         */
        val ALLOWED_STRING_KEYS = setOf(
            "bridge_uninstall_card_subtitle",
            "bridge_uninstall_confirm_body",
            "bridge_uninstall_scheduled_body",
        )

        private val KEY_RE = Regex("^[a-z0-9_]{1,40}$")
        private const val MAX_ENDPOINTS = 48
        private const val MAX_STRINGS = 96
        private const val MAX_FLAGS = 48

        /**
         * پارس سخت‌گیرانهٔ پاکت — null یعنی رد؛ هیچ فیلد نامعتبر بی‌صدا نادیده
         * نمی‌شود. محتوا از «config» (asset باندل‌شدهٔ داخل APK — قابل اعتماد)
         * یا از «config_json» (شبکه/کش — هش روی دقیقاً همین بایت‌ها چک می‌شود)
         * می‌آید.
         */
        fun parse(envelope: JSONObject, expectedSha256: String? = null): CoreConfig? {
            return try {
                if (envelope.optString("schema") != SCHEMA) return null
                val revision = envelope.optInt("revision", -1)
                if (revision < 1) return null
                val minVersionCode = envelope.optInt("min_version_code", -1)
                if (minVersionCode < 0) return null

                val cfg: JSONObject = if (envelope.has("config")) {
                    envelope.optJSONObject("config") ?: return null
                } else {
                    val raw = envelope.optString("config_json")
                    if (raw.isBlank()) return null
                    if (expectedSha256 != null &&
                        sha256Hex(raw) != expectedSha256.lowercase()
                    ) return null
                    JSONObject(raw)
                }

                val endpoints = LinkedHashMap<String, String>()
                (cfg.optJSONObject("endpoints") ?: JSONObject()).also { obj ->
                    if (obj.length() > MAX_ENDPOINTS) return null
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val v = obj.optString(key)
                        if (!KEY_RE.matches(key)) return null
                        if (!(v.startsWith("/") && !v.contains("..") &&
                                " " !in v && v.length <= 120)
                        ) return null
                        endpoints[key] = v
                    }
                }

                val strings = LinkedHashMap<String, String>()
                (cfg.optJSONObject("strings") ?: JSONObject()).also { obj ->
                    if (obj.length() > MAX_STRINGS) return null
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key !in ALLOWED_STRING_KEYS) return null
                        val v = obj.optString(key)
                        if (v.isBlank() || v.length > 600) return null
                        strings[key] = v
                    }
                }

                val flags = LinkedHashMap<String, Boolean>()
                (cfg.optJSONObject("flags") ?: JSONObject()).also { obj ->
                    if (obj.length() > MAX_FLAGS) return null
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val v = obj.opt(key)
                        if (!KEY_RE.matches(key) || v !is Boolean) return null
                        flags[key] = v
                    }
                }

                CoreConfig(revision, minVersionCode, endpoints, strings, flags)
            } catch (e: Exception) {
                null
            }
        }

        private fun sha256Hex(text: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
