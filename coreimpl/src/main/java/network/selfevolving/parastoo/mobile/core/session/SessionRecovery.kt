package network.selfevolving.parastoo.mobile.core.session

import android.content.Context
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import network.selfevolving.parastoo.mobile.core.data.Db
import network.selfevolving.parastoo.mobile.core.data.ServerProfileEntity
import network.selfevolving.parastoo.mobile.core.net.AccessAgentApi
import network.selfevolving.parastoo.mobile.core.security.CryptoFix
import network.selfevolving.parastoo.mobile.core.security.PinnedTrust
import network.selfevolving.parastoo.mobile.core.security.SecretVault
import java.security.MessageDigest

/**
 * بازیابی درون‌سیستمی نشست ادمین — فرمان مالک ۲۰۲۶-۱۱-۱۲ («الگوی مخربِ آپدیت»):
 * «کاربر را درگیر رمز بازی و توکن بازی نکن — یک‌بار رمز را بگیر و تمام؛ اگر
 * قرار است رمزی هم تغییر کند، این باید یک فرایند درون‌سیستمی باشد و کاربر
 * درگیر ارسال دوبارهٔ رمز نشود.»
 *
 * مشکل میدانی: پس از فرایندهای بروزرسانی، اپ سرور را «لود اطلاعات ناموفق»
 * نشان می‌داد و تنها راه پیشنهادی، حذف و افزودن دوباره با آی‌پی و رمز بود —
 * یعنی دقیقاً همان «رمز بازی» که ممنوع شد. بررسی میدانی روی سرور زنده نشان داد
 * گواهی و توکن‌های سرور سالم مانده‌اند؛ آنچه می‌میرد توکنِ ذخیره‌شدهٔ گوشی است
 * (گم‌شدن از گاوصندوق یا کهنگی پس از تغییر سمت سرور).
 *
 * راه‌حل این کلاس — بازیابی بی‌سروصدا با رازهای همان گاوصندوق (هیچ‌چیز از
 * کاربر پرسیده نمی‌شود):
 *  ۱) SSH با رمزِ ذخیره‌شده + کلید میزبانِ پین‌شده (کلید میزبان لنگر اعتماد است).
 *  ۲) خواندن توکن ادمینِ فعلیِ سرور از /etc/access-agent/secrets/admin-token.
 *  ۳) TOFU گواهی از مسیر عمومی؛ اگر گواهی عوض شده باشد ولی کلید میزبان SSH
 *     همان است (نشانهٔ نصب مجددِ همان سرور)، پین گواهی در پروفایل درون‌سیستمی
 *     تازه می‌شود. اگر کلید میزبان عوض شده باشد، مرز امنیتی واقعی است و بازیابی
 *     با صداقت شکست می‌خورد — نه سکوتِ TOFUِ مجدد.
 *  ۴) تأیید نهایی /api/status با توکنِ تازه؛ سپس ذخیره در گاوصندوق.
 *
 * اگر رمز SSH در گاوصندوق نباشد (اولین بار / پس از ریست دستگاه) بازیابی ممکن
 * نیست و همان یک بار از مالک پرسیده می‌شود — قانون «یک‌بار رمز» همچنان برقرار.
 */
object SessionRecovery {

    class RecoveryResult(val profile: ServerProfileEntity, val adminToken: String)

    /**
     * بازیابی بی‌سروصدا (suspend — همیشه از Dispatchers.IO صدا زده شود).
     * null یعنی ممکن نشد (دلیلش را با [why] می‌شود خواند).
     * هرگز استثنا نمی‌اندازد — بازیابیِ ناموفق، خطای اپ نیست.
     */
    suspend fun recover(context: Context, profile: ServerProfileEntity, why: (String) -> Unit = {}): RecoveryResult? {
        val password = SecretVault.getSshPassword(context, profile.id)
        if (password.isBlank()) {
            why("رمز SSH در گاوصندوق نیست — بازیابی درون‌سیستمی ممکن نیست")
            return null
        }
        val host = hostOf(profile.url)
        if (host.isNullOrBlank()) {
            why("آدرس سرور قابل تجزیه نیست")
            return null
        }
        val pinnedHostKey = SecretVault.getSshHostKeyFp(context, profile.id)
        if (pinnedHostKey.isBlank()) {
            why("اثرانگشت کلید میزبان در گاوصندوق نیست — بازیابی ممکن نیست")
            return null
        }
        val sshPort = SecretVault.getSshPort(context, profile.id)

        CryptoFix.ensure()
        val cfg = net.schmizz.sshj.DefaultConfig()
        if (!CryptoFix.x25519Available()) {
            val withoutCurve = cfg.keyExchangeFactories
                .filterNot { it.name.startsWith("curve25519") }
            cfg.setKeyExchangeFactories(withoutCurve)
        }
        val ssh = SSHClient(cfg)
        try {
            ssh.setConnectTimeout(20_000)
            ssh.setTimeout(60_000)
            // لنگر اعتماد: فقط کلید میزبانِ پین‌شدهٔ همین سرور پذیرفته می‌شود.
            // تغییر کلید میزبان = مرز امنیتی واقعی = شکست صادقانه، نه TOFUِ خاموش.
            ssh.addHostKeyVerifier(object : HostKeyVerifier {
                override fun verify(hostname: String?, port: Int, key: java.security.PublicKey?): Boolean {
                    if (key == null) return false
                    val got = sha256Hex(key.encoded)
                    return got.equals(pinnedHostKey, ignoreCase = true)
                }

                override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> = emptyList()
            })
            ssh.connect(host, sshPort)
            try {
                ssh.authPassword("root", password)
            } catch (e: UserAuthException) {
                // رمز روی سرور عوض شده — قانون «یک‌بار» اجازه می‌دهد فقط همین
                // یک بار دوباره پرسیده شود؛ گاوصندوق پاک شده تا دوباره نخواهد.
                SecretVault.deleteSshPassword(context, profile.id)
                why("سرور رمز SSH را رد کرد — رمز روی سرور عوض شده است")
                return null
            }

            // توکن ادمینِ فعلیِ سرور — نه ساختِ تازه، نه چرخش: همگامی بی‌درز
            val adminToken = ssh.startSession().use { s ->
                val c = s.exec("cat /etc/access-agent/secrets/admin-token 2>/dev/null || true")
                val text = String(c.inputStream.readBytes(), Charsets.UTF_8).trim()
                c.join()
                text
            }
            if (!adminToken.startsWith("adm_") || adminToken.length < 10) {
                why("توکن ادمین روی سرور خوانده نشد — پل نصب نیست؟")
                return null
            }

            // پیوستگی گواهی: TOFU از مسیر عمومی؛ تفاوت + کلید میزبانِ ثابت =
            // نصب مجدد همان سرور → پینِ پروفایل درون‌سیستمی تازه می‌شود.
            var effectiveProfile = profile
            val netFp = PinnedTrust.fetchFingerprint(profile.url)
            if (!netFp.equals(profile.certSha256, ignoreCase = true)) {
                effectiveProfile = ServerProfileEntity(
                    id = profile.id,
                    name = profile.name,
                    url = profile.url,
                    certSha256 = netFp,
                    createdAtMs = profile.createdAtMs,
                )
                Db.get().serverDao().update(effectiveProfile)
            }

            // تأیید نهایی با توکنِ خوانده‌شده — بازیابی تا وقتی «کار می‌کند» تمام نشده
            val api = AccessAgentApi(effectiveProfile.url, netFp, adminToken)
            api.status()

            SecretVault.saveAdminToken(context, effectiveProfile.id, adminToken)
            return RecoveryResult(effectiveProfile, adminToken)
        } catch (e: Exception) {
            why(e.message ?: e.javaClass.simpleName)
            return null
        } finally {
            runCatching { ssh.disconnect() }
        }
    }

    /** host از URL پروفایل (https://host:port) — بدون مسیر و پورت */
    private fun hostOf(url: String): String? {
        val s = url.trim().removePrefix("https://").removePrefix("http://")
        val h = s.substringBefore('/')
        return h.substringBefore(':').takeIf { it.isNotBlank() }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02X".format(it) }
}
