package network.selfevolving.parastoo.mobile.core.provision

import android.content.Context
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.xfer.FileSystemFile
import network.selfevolving.parastoo.mobile.core.model.HostStatus
import network.selfevolving.parastoo.mobile.core.net.AccessAgentApi
import network.selfevolving.parastoo.mobile.core.security.CryptoFix
import network.selfevolving.parastoo.mobile.core.security.PinnedTrust
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest
import java.security.SecureRandom
import org.json.JSONObject

/**
 * فراهم‌سازی کامل سرور تازه از داخل اپ — سناریوی مالک (2026-11-08):
 * «کاربر فقط آدرس + رمز سرور را یک بار به پرستو موبایل می‌دهد؛ اپ با SSH پل
 * (آجنت دسترسی) را نصب می‌کند و از آن پس همه‌چیز از داخل اپ است.»
 *
 * توالی:
 *  ۱) بستهٔ پل: اول کانال انتشار گیت‌هاب (§۸ قرارداد پل) با صحت دو-کاناله؛
 *     در نبودِ شبکه، باندل جاسازی‌شدهٔ APK (ممکن است کهنه‌تر باشد) فقط برای سرور نو
 *  ۲) اتصال SSH با رمز — TOFU کلید میزبان (اولین اتصال ثبت می‌شود)
 *  ۳) بالا بردن بسته + فایل env (توکن‌های ساخته‌شده روی همین گوشی) با SFTP
 *  ۴) اجرای install.sh آجنت — نصب/ارتقای idempotent؛ توکن‌ها هرگز در خروجی چاپ نمی‌شوند
 *  ۵) TOFU گواهی TLS از مسیر عمومی + تطبیق با CERT_SHA256 گزارش نصب
 *  ۶) تست نهایی /api/status با توکن ادمین تازه
 *  ۷) اختیاری — توکن گیت‌هاب مالک (فرمان مالک، 2026-11-09): اگر کاربر در فرم داده
 *     باشد، با API ادمین پل به‌عنوان «یک‌جای مناسب سرور» ذخیره می‌شود (github.env
 *     با دسترسی 600) تا پل بتواند به عامل توکن موقتِ هدفمند بدهد. توکن فقط در
 *     همین فراخوانی در حافظه است؛ نه ذخیره می‌شود نه لاگ. شکست این مرحله نصب را
 *     نمی‌شکند — فقط گزارش می‌شود (داشبورد بعداً جبران می‌کند).
 *
 * قرارداد مالکیت (فرمان مالک، 2026-11-08): «رمز روت = مالکیت» — نصب همیشه توکن‌های
 * تازهٔ این گوشی را جایگزین می‌کند (ACCESS_AGENT_RESET_TOKENS=1)؛ چه سرور تازه باشد
 * چه از قبل پل داشته باشد. هیچ گزینه‌ای از کاربر پرسیده نمی‌شود.
 *
 * رمز سرور (فرمان مالک، 2026-11-09): «یک بار گرفته می‌شود و تمام — پرستو موبایل
 * امن‌ترین جای نگه‌داری است؛ پرسیدن مکرر، امنیت کمتر و خطای انسانی بیشتر است.»
 * پس پس از نصب موفق، رمز/پورت/اثرانگشت کلید میزبان در گاوصندوق اپ (SecretVault —
 * AES256-GCM با کلید سخت‌افزاری گوشی) ذخیره می‌شوند تا به‌روزرسانی خودکار پل و
 * بقیهٔ کارها بدون هیچ پرسشی انجام شوند؛ این کلاس خودش چیزی نمی‌نویسد.
 */
class ServerProvisioner {

    data class ProvisionResult(
        val agentUrl: String,
        val certSha256: String,
        val adminToken: String,
        val agentToken: String,
        val sshHostKeyFp: String,
        val hostname: String,
        val agentVersion: String,
        val githubTokenSet: Boolean? = null, // null = اصلاً تلاش نشد (توکنی داده نشد)
        val githubLogin: String = "",       // اگر سرور توانست اعتبارسنجی کند
    )

    /** باندل جاسازی‌شدهٔ APK — فقط فالبک آفلاین برای سرور نو؛ صحتش با sha256 داخل APK */
    private fun embeddedRelease(context: Context): BridgeReleaseClient.BridgeRelease {
        // پسوند خنثی (.bin): AAPT پسوند gz را از اسم asset حذف می‌کند — r8 آموختیم
        val bytes = context.assets.open("provision/access-agent-bundle.bin").use { it.readBytes() }
        val expectedSha = context.assets.open("provision/access-agent-bundle.sha256")
            .bufferedReader().readLine().trim().split(Regex("\\s+")).first()
        val actualSha = sha256Hex(bytes)
        if (!expectedSha.equals(actualSha, ignoreCase = true)) {
            throw IllegalStateException("بستهٔ جاسازی‌شده ناقص/دستکاری‌شده است (SHA-256 نمی‌خواند)")
        }
        return BridgeReleaseClient.BridgeRelease(
            version = "bundled",
            filename = "access-agent-bundle.bin",
            sha256 = actualSha,
            bytes = bytes,
            source = "باندل جاسازی‌شدهٔ اپ (آفلاین)",
        )
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02X".format(it) }

    private fun newToken(prefix: String): String {
        val b = ByteArray(24)
        SecureRandom().nextBytes(b)
        return prefix + b.joinToString("") { "%02x".format(it) }
    }

    /**
     * @param onPhase فاز فعلی برای نمایش در UI
     * @param onLog خطوط لاگ زندهٔ نصب‌کننده (خروجی سرور)
     */
    fun provision(
        context: Context,
        host: String,
        sshPort: Int,
        password: String,
        agentPort: Int,
        githubToken: String = "",
        onPhase: (String) -> Unit = {},
        onLog: (String) -> Unit = {},
    ): ProvisionResult {
        require(host.isNotBlank()) { "آدرس سرور خالی است" }
        require(password.isNotBlank()) { "رمز سرور خالی است" }
        require(agentPort in 1..65535) { "پورت پل نامعتبر است" }
        require(sshPort in 1..65535) { "پورت SSH نامعتبر است" }

        // تضمین BC کامل پیش از هر کار sshj (رفع «no such algorithm: X25519»)
        CryptoFix.ensure()

        // ---------- ۱) بستهٔ پل: کانال انتشار گیت‌هاب، فالبک باندل اپ (سرور نو) ----------
        onPhase("گرفتن آخرین بستهٔ پل از گیت‌هاب")
        val release = try {
            BridgeReleaseClient.fetchLatest(onLog)
        } catch (e: Exception) {
            onLog("کانال گیت‌هاب باز نشد (${e.message}) — باندل جاسازی‌شدهٔ اپ استفاده می‌شود (ممکن است کهنه‌تر باشد؛ فقط برای سرور نو)")
            embeddedRelease(context)
        }
        val bundleBytes = release.bytes
        onLog("بستهٔ پل v${release.version} آماده است — ${bundleBytes.size} بایت (${release.source})")

        val adminToken = newToken("adm_")
        // v0.5.6 — فرمان مالک ۲۰۲۶-۱۱-۱۲ («الگوی مخربِ آپدیت»: کاربر را درگیر
        // رمز/توکن بازی نکن): توکنِ عاملِ موجود روی سرور (اگر پل از قبل نصب است)
        // بعد از اتصال SSH خوانده و «نگه داشته» می‌شود — قلمرو عامل بی‌درز زنده
        // می‌ماند و ارتباط عامل هوشمند با پل قطع نمی‌شود؛ ادمین همچنان توکنِ
        // تازهٔ این گوشی می‌گیرد (مالکیت). فقط سرورِ بدون پل، توکن عامل تازه می‌گیرد.
        var agentToken = newToken("aa_")

        val tmpDir = File(context.cacheDir, "provision").apply { mkdirs() }
        val localBundle = File(tmpDir, "access-agent-bundle.tar.gz")
        val localEnv = File(tmpDir, "provision.env")
        val stamp = System.currentTimeMillis()
        val remoteBundle = "/tmp/parastoo-provision-$stamp.tar.gz"
        val remoteEnv = "/tmp/parastoo-provision-$stamp.env"
        val remoteWork = "/tmp/parastoo-provision-$stamp"

        localBundle.writeBytes(bundleBytes)
        localEnv.deleteOnExit(); localBundle.deleteOnExit()

        // اسکریپت اجرا روی سرور — هیچ رازی داخلش نیست (رازها در فایل env با chmod 600)
        val script = buildString {
            appendLine("set -e")
            appendLine("chmod 600 '$remoteEnv'")
            appendLine("rm -rf '$remoteWork' && mkdir -p '$remoteWork'")
            appendLine("tar -xzf '$remoteBundle' -C '$remoteWork'")
            appendLine("set -a; . '$remoteEnv'; set +a")
            appendLine("cd '$remoteWork'")
            appendLine("bash install.sh '$remoteWork' 2>&1 || RC=\$?")
            appendLine("rm -rf '$remoteWork' '$remoteBundle' '$remoteEnv'")
            appendLine("exit \${RC:-0}")
        }

        // کانفیگ پیش‌فرض sshj؛ SSHClient کانفیگ را با getter بیرون نمی‌دهد — از سازنده پاس می‌دهیم
        val cfg = net.schmizz.sshj.DefaultConfig()
        // فالبک ضدتوقف: اگر به هر دلیلی X25519 در این دستگاه نبود، curve25519 از
        // فهرست تبادل کلید حذف می‌شود تا نصب با ecdh-sha2-nistp256 پیش برود.
        if (!CryptoFix.x25519Available()) {
            val withoutCurve = cfg.keyExchangeFactories
                .filterNot { it.name.startsWith("curve25519") }
            cfg.setKeyExchangeFactories(withoutCurve)
            onLog("هشدار: X25519 در این دستگاه نیست — تبادل کلید با ECDH انجام می‌شود")
        }
        val ssh = SSHClient(cfg)
        try {
            // ---------- ۲) اتصال SSH — TOFU کلید میزبان ----------
            onPhase("اتصال SSH به $host:$sshPort")
            ssh.setConnectTimeout(20_000)
            ssh.setTimeout(180_000)
            // مدل SSH/TOFU: اولین اتصال پذیرفته و اثرانگشتش همین‌جا ثبت و نشان داده
            // می‌شود؛ نصب دوبارهٔ همین سرور در نسخه‌های بعدی با known_hosts سخت‌گیر می‌شود.
            var sshHostKeyFp = ""
            ssh.addHostKeyVerifier(object : HostKeyVerifier {
                override fun verify(hostname: String?, port: Int, key: java.security.PublicKey?): Boolean {
                    if (key != null) {
                        sshHostKeyFp = sha256Hex(key.encoded)
                        onLog("کلید میزبان (${key.algorithm}) ثبت شد: ${sshHostKeyFp.take(16)}…")
                    }
                    return true
                }

                override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> = emptyList()
            })
            ssh.connect(host, sshPort)

            // ---------- ۳) احراز با رمز + بالا بردن بسته ----------
            onPhase("احراز هویت با رمز سرور")
            ssh.authPassword("root", password)
            onLog("ورود موفق به‌عنوان root")

            // ---------- ۲ب) پیوستگی قلمرو عامل (v0.5.6 — فرمان مالک ۲۰۲۶-۱۱-۱۲) ----------
            // «الگوی مخربِ آپدیت» حذف شد: فراهم‌سازی مجدد دیگر توکنِ عاملِ زنده را
            // نمی‌کُشد. اگر پل از قبل روی سرور نصب است، توکن عاملِ فعلی‌اش را می‌خوانیم
            // و همان را نگه می‌داریم (تغییرش لازم نبود — درون‌سیستمی)؛ ارتباط عامل
            // هوشمند با پل قطع نمی‌شود. سرورِ بدون پل، توکن تازه می‌گیرد.
            runCatching {
                ssh.startSession().use { s ->
                    val c = s.exec("cat /etc/access-agent/secrets/agent-token 2>/dev/null || true")
                    val text = String(c.inputStream.readBytes(), Charsets.UTF_8).trim()
                    c.join()
                    if (text.startsWith("aa_") && text.length >= 10) {
                        agentToken = text
                        onLog("توکن عاملِ موجود نگه داشته شد — قلمرو عامل بی‌درز ادامه می‌یابد")
                    }
                }
            }.onFailure {
                onLog("خواندن توکن عامل موجود ممکن نشد (سرور بدون پل؟) — توکن تازه ساخته می‌شود")
            }

            // «رمز روت = مالکیت» برای قلمرو ادمین: توکن ادمینِ تازهٔ این گوشی
            // جایگزین می‌شود؛ فایل env همین‌جا نوشته می‌شود (توکن عامل شاید از
            // سرور خوانده و نگه داشته شده باشد). بدون هیچ سؤالی از کاربر.
            localEnv.writeText(
                buildString {
                    appendLine("ACCESS_AGENT_PORT=$agentPort")
                    appendLine("ACCESS_AGENT_ADMIN_TOKEN=$adminToken")
                    appendLine("ACCESS_AGENT_AGENT_TOKEN=$agentToken")
                    appendLine("ACCESS_AGENT_RESET_TOKENS=1")
                }
            )

            onPhase("بالا بردن بستهٔ نصب (SFTP)")
            ssh.newSFTPClient().use { sftp ->
                sftp.put(FileSystemFile(localBundle.absolutePath), remoteBundle)
                sftp.put(FileSystemFile(localEnv.absolutePath), remoteEnv)
            }
            onLog("بسته و فایل پیکربندی بالا رفت")

            // ---------- ۴) اجرای نصب‌کننده ----------
            onPhase("اجرای نصب‌کنندهٔ پل روی سرور")
            var accessUrlParsed: String? = null
            var certShaParsed: String? = null
            var exitCode = -1
            ssh.startSession().use { session ->
                val c = session.exec(script)
                val reader = BufferedReader(InputStreamReader(c.inputStream, Charsets.UTF_8))
                while (true) {
                    val line = reader.readLine() ?: break
                    onLog(line)
                    val t = line.trim()
                    if (t.startsWith("ACCESS_URL:")) accessUrlParsed = t.removePrefix("ACCESS_URL:").trim()
                    if (t.startsWith("CERT_SHA256:")) {
                        certShaParsed = t.removePrefix("CERT_SHA256:").trim().replace(":", "")
                    }
                }
                c.join()
                exitCode = c.exitStatus ?: -1
            }
            if (exitCode != 0) {
                throw IllegalStateException("نصب‌کننده روی سرور شکست خورد (کد $exitCode) — لاگ بالا را ببینید")
            }

            // ---------- ۵) تأیید TLS از بیرون (TOFU گواهی) ----------
            onPhase("تأیید گواهی TLS پل (TOFU)")
            val agentUrl = accessUrlParsed ?: "https://$host:$agentPort"
            val netFp = PinnedTrust.fetchFingerprint(agentUrl)
            val certSha256 = certShaParsed?.uppercase()
            if (certSha256 != null && certSha256 != netFp) {
                throw IllegalStateException(
                    "گواهی شبکه با گزارش نصب نمی‌خواند — احتمال واسطهٔ مخرب. اتصال متوقف شد."
                )
            }
            onLog("اثرانگشت گواهی تأیید شد: ${netFp.take(16)}…")

            // ---------- ۶) تست نهایی با توکن ادمین تازه ----------
            // سرویس تازه ری‌استارت شده؛ چند بار با فاصلهٔ کوتاه امتحان می‌کنیم تا
            // «هنوز بالا نیامده» با «خطای واقعی» اشتباه گرفته نشود.
            onPhase("تست نهایی API پل")
            val api = AccessAgentApi(agentUrl, netFp, adminToken)
            var lastApiError: AccessAgentApi.ApiException? = null
            var st: HostStatus? = null
            for (attempt in 1..3) {
                try {
                    st = api.status()
                    lastApiError = null
                    break
                } catch (e: AccessAgentApi.ApiException) {
                    lastApiError = e
                    if (e.code != 401 && e.code != 403 && e.code != 502 && e.code != 503) throw e
                    if (attempt < 3) {
                        try { Thread.sleep(2_500L) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                    }
                }
            }
            val status = st ?: run {
                val e = lastApiError
                throw IllegalStateException(
                    "نصب کامل شد ولی تأیید نهایی ناکام ماند" +
                        (e?.let { " (کد ${it.code})" } ?: "") +
                        ". چند لحظه بعد دوباره تلاش کنید.",
                    e,
                )
            }
            // ---------- ۷) اختیاری: توکن گیت‌هاب مالک — «یک‌جای مناسب سرور» ----------
            var ghSet: Boolean? = null
            var ghLogin = ""
            if (githubToken.isNotBlank()) {
                onPhase("آماده‌سازی توکن گیت‌هاب")
                try {
                    val res = api.setGithubToken(githubToken.trim())
                    ghSet = res.optBoolean("stored", false)
                    val v = res.optJSONObject("validation")
                    ghLogin = v?.optString("login") ?: ""
                    if (ghSet == true) {
                        onLog(
                            if (ghLogin.isNotBlank()) "توکن گیت‌هاب روی سرور ذخیره شد (حساب: $ghLogin)"
                            else "توکن گیت‌هاب روی سرور ذخیره شد (اعتبارسنجی آنلاین از سرور ممکن نشد — طبیعی در برخی شبکه‌ها)"
                        )
                    } else {
                        onLog("ذخیرهٔ توکن گیت‌هاب تأیید نشد — بعداً از داشبورد می‌توانید اضافه کنید")
                    }
                } catch (e: Exception) {
                    ghSet = false
                    onLog("ذخیرهٔ توکن گیت‌هاب ناکام ماند (${e.message}) — نصب سالم است؛ از داشبورد دوباره تلاش کنید")
                }
            }

            onPhase("انجام شد")
            onLog("پل v${status.version} روی «${status.hostname}» آماده است")

            return ProvisionResult(
                agentUrl = agentUrl,
                certSha256 = netFp,
                adminToken = adminToken,
                agentToken = agentToken,
                sshHostKeyFp = sshHostKeyFp,
                hostname = status.hostname,
                agentVersion = status.version,
            )
        } finally {
            runCatching { ssh.disconnect() }
            localEnv.delete()
            localBundle.delete()
        }
    }
}
