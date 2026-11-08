package network.selfevolving.parastoo.mobile.core.provision

import android.content.Context
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.xfer.FileSystemFile
import network.selfevolving.parastoo.mobile.core.net.AccessAgentApi
import network.selfevolving.parastoo.mobile.core.security.PinnedTrust
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * فراهم‌سازی کامل سرور تازه از داخل اپ — سناریوی مالک (2026-11-08):
 * «کاربر فقط آدرس + رمز سرور را یک بار به پرستو موبایل می‌دهد؛ اپ با SSH پل
 * (آجنت دسترسی) را نصب می‌کند و از آن پس همه‌چیز از داخل اپ است.»
 *
 * توالی:
 *  ۱) صحت بستهٔ نصب داخل APK (SHA-256 ثبت‌شده کنار بسته)
 *  ۲) اتصال SSH با رمز — TOFU کلید میزبان (اولین اتصال ثبت می‌شود)
 *  ۳) بالا بردن بسته + فایل env (توکن‌های ساخته‌شده روی همین گوشی) با SFTP
 *  ۴) اجرای install.sh آجنت — نصب/ارتقای idempotent؛ توکن‌ها هرگز در خروجی چاپ نمی‌شوند
 *  ۵) TOFU گواهی TLS از مسیر عمومی + تطبیق با CERT_SHA256 گزارش نصب
 *  ۶) تست نهایی /api/status با توکن ادمین تازه
 *
 * رمز سرور فقط در همین فراخوانی استفاده و «هیچ‌گاه» ذخیره نمی‌شود.
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
    )

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
        resetTokens: Boolean = false,
        onPhase: (String) -> Unit = {},
        onLog: (String) -> Unit = {},
    ): ProvisionResult {
        require(host.isNotBlank()) { "آدرس سرور خالی است" }
        require(password.isNotBlank()) { "رمز سرور خالی است" }
        require(agentPort in 1..65535) { "پورت پل نامعتبر است" }
        require(sshPort in 1..65535) { "پورت SSH نامعتبر است" }

        // ---------- ۱) بستهٔ نصب داخل APK ----------
        onPhase("بررسی بستهٔ نصب داخل اپ")
        // پسوند خنثی (.bin): AAPT پسوند gz را از اسم asset حذف می‌کند — r8 آموختیم
        val bundleBytes = context.assets.open("provision/access-agent-bundle.bin").use { it.readBytes() }
        val expectedSha = context.assets.open("provision/access-agent-bundle.sha256")
            .bufferedReader().readLine().trim().split(Regex("\\s+")).first()
        val actualSha = sha256Hex(bundleBytes)
        if (!expectedSha.equals(actualSha, ignoreCase = true)) {
            throw IllegalStateException("بستهٔ نصب ناقص/دستکاری‌شده است (SHA-256 نمی‌خواند)")
        }
        onLog("بستهٔ نصب سالم است — ${bundleBytes.size} بایت")

        val adminToken = newToken("adm_")
        val agentToken = newToken("aa_")

        val tmpDir = File(context.cacheDir, "provision").apply { mkdirs() }
        val localBundle = File(tmpDir, "access-agent-bundle.tar.gz")
        val localEnv = File(tmpDir, "provision.env")
        val stamp = System.currentTimeMillis()
        val remoteBundle = "/tmp/parastoo-provision-$stamp.tar.gz"
        val remoteEnv = "/tmp/parastoo-provision-$stamp.env"
        val remoteWork = "/tmp/parastoo-provision-$stamp"

        localBundle.writeBytes(bundleBytes)
        localEnv.writeText(
            buildString {
                appendLine("ACCESS_AGENT_PORT=$agentPort")
                appendLine("ACCESS_AGENT_ADMIN_TOKEN=$adminToken")
                appendLine("ACCESS_AGENT_AGENT_TOKEN=$agentToken")
                if (resetTokens) appendLine("ACCESS_AGENT_RESET_TOKENS=1")
            }
        )
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

        val ssh = SSHClient()
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
            onPhase("تست نهایی API پل")
            val api = AccessAgentApi(agentUrl, netFp, adminToken)
            val st = try {
                api.status()
            } catch (e: AccessAgentApi.ApiException) {
                if (e.code == 401 || e.code == 403) {
                    throw IllegalStateException(
                        "این سرور از قبل پل دارد و توکنش با توکن تازهٔ این گوشی نمی‌خواند. " +
                            "اگر مالک همان سرور هستید، در «تنظیمات پیشرفته» گزینهٔ «انتقال مالکیت» را " +
                            "بزنید تا توکن‌ها به این گوشی منتقل و توکن‌های قبلی باطل شوند.",
                        e,
                    )
                }
                throw e
            }
            onPhase("انجام شد")
            onLog("پل v${st.version} روی «${st.hostname}» آماده است")

            return ProvisionResult(
                agentUrl = agentUrl,
                certSha256 = netFp,
                adminToken = adminToken,
                agentToken = agentToken,
                sshHostKeyFp = sshHostKeyFp,
                hostname = st.hostname,
                agentVersion = st.version,
            )
        } finally {
            runCatching { ssh.disconnect() }
            localEnv.delete()
            localBundle.delete()
        }
    }
}
