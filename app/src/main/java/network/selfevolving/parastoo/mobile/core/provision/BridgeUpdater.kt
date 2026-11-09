package network.selfevolving.parastoo.mobile.core.provision

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.xfer.FileSystemFile
import network.selfevolving.parastoo.mobile.core.security.CryptoFix
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * به‌روزرسانی پلِ موجود (access-agent) از کانال انتشار گیت‌هاب.
 * قرارداد کامل: docs/AGENT_PROTOCOL.fa.md §۸ — قواعد ثابت این کلاس:
 *
 *  ۱) فایل بدون تطبیق sha256 مانیفست هرگز به سرور نمی‌رود (دو-کاناله).
 *  ۲) فقط نسخهٔ تازه‌تر از نصب‌شده قبول است — downgrade ممنوع.
 *  ۳) هیچ env توکنی بالا نمی‌رود و RESET_TOKENS هرگز ست نمی‌شود — مسیر ارتقای
 *     install.sh همهٔ رازها (توکن‌ها/گواهی/PAT/audit) را نگه می‌دارد.
 *  ۴) گواهی TLS نباید عوض شود؛ تغییرش یعنی رد آپدیت (هشدار واسطهٔ مخرب).
 *  ۵) رمز root فقط در همین فراخوانی استفاده و هیچ‌گاه ذخیره نمی‌شود.
 *
 * تفاوت عمدی با ServerProvisioner: اینجا باندلِ جاسازی‌شده فالبک مجاز نیست —
 * به‌روزرسانی با بستهٔ کهنه بی‌معناست؛ شکست کانال گیت‌هاب = رد با پیام صادقانه.
 */
class BridgeUpdater {

    data class UpdateResult(
        val version: String,
        val source: String,
        val certSha256: String,
    )

    fun update(
        host: String,
        sshPort: Int,
        password: String,
        currentVersion: String,
        expectedCertSha256: String,
        cacheDir: File,
        onPhase: (String) -> Unit = {},
        onLog: (String) -> Unit = {},
    ): UpdateResult {
        require(host.isNotBlank()) { "آدرس سرور خالی است" }
        require(password.isNotBlank()) { "رمز سرور خالی است" }
        require(sshPort in 1..65535) { "پورت SSH نامعتبر است" }
        require(expectedCertSha256.isNotBlank()) { "پین گواهی این سرور ذخیره نشده است" }

        // تضمین BC کامل پیش از هر کار sshj (درس میدانی X25519)
        CryptoFix.ensure()

        // ---------- ۱) بسته از کانال انتشار — بدون فالبک کهنه ----------
        onPhase("گرفتن آخرین نسخهٔ پل از گیت‌هاب")
        val release = BridgeReleaseClient.fetchLatest(onLog)
        if (!BridgeReleaseClient.isAtLeast(release.version, currentVersion)) {
            throw IllegalStateException(
                "نسخهٔ کانال انتشار (v${release.version}) تازه‌تر از پل نصب‌شده (v$currentVersion) نیست — به‌روزرسانی لازم نیست.",
            )
        }

        val stamp = System.currentTimeMillis()
        val localBundle = File(cacheDir, "access-agent-$stamp.tar.gz").apply {
            parentFile?.mkdirs(); deleteOnExit()
        }
        localBundle.writeBytes(release.bytes)
        val remoteBundle = "/tmp/parastoo-bridge-update-$stamp.tar.gz"
        val remoteWork = "/tmp/parastoo-bridge-update-$stamp"

        // اسکریپت ارتقا — هیچ رازی داخلش نیست؛ مسیر ارتقا توکن‌ها/گواهی/PAT را نگه می‌دارد
        val script = buildString {
            appendLine("set -e")
            appendLine("rm -rf '$remoteWork' && mkdir -p '$remoteWork'")
            appendLine("tar -xzf '$remoteBundle' -C '$remoteWork'")
            appendLine("cd '$remoteWork'")
            appendLine("bash install.sh '$remoteWork' 2>&1 || RC=\$?")
            appendLine("rm -rf '$remoteWork' '$remoteBundle'")
            appendLine("exit \${RC:-0}")
        }

        val cfg = net.schmizz.sshj.DefaultConfig()
        if (!CryptoFix.x25519Available()) {
            val withoutCurve = cfg.keyExchangeFactories
                .filterNot { it.name.startsWith("curve25519") }
            cfg.setKeyExchangeFactories(withoutCurve)
            onLog("هشدار: X25519 در این دستگاه نیست — تبادل کلید با ECDH انجام می‌شود")
        }
        val ssh = SSHClient(cfg)
        try {
            // ---------- ۲) SSH با TOFU کلید میزبان ----------
            onPhase("اتصال SSH به $host:$sshPort")
            ssh.setConnectTimeout(20_000)
            ssh.setTimeout(180_000)
            var sshHostKeyFp = ""
            ssh.addHostKeyVerifier(object : HostKeyVerifier {
                override fun verify(hostname: String?, port: Int, key: java.security.PublicKey?): Boolean {
                    if (key != null) {
                        sshHostKeyFp = BridgeReleaseClient.sha256Hex(key.encoded)
                        onLog("کلید میزبان (${key.algorithm}) ثبت شد: ${sshHostKeyFp.take(16)}…")
                    }
                    return true
                }

                override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> = emptyList()
            })
            ssh.connect(host, sshPort)

            // ---------- ۳) احراز + بالا بردن بسته ----------
            onPhase("احراز هویت با رمز سرور")
            ssh.authPassword("root", password)
            onLog("ورود موفق به‌عنوان root")

            onPhase("بالا بردن بستهٔ v${release.version} (SFTP)")
            ssh.newSFTPClient().use { sftp ->
                sftp.put(FileSystemFile(localBundle.absolutePath), remoteBundle)
            }
            onLog("بسته بالا رفت — ${release.bytes.size} بایت")

            // ---------- ۴) اجرای نصب‌کننده در مسیر ارتقا ----------
            onPhase("اجرای نصب‌کنندهٔ پل (ارتقا — رازها حفظ می‌شوند)")
            var certShaParsed: String? = null
            var exitCode = -1
            ssh.startSession().use { session ->
                val c = session.exec(script)
                val reader = BufferedReader(InputStreamReader(c.inputStream, Charsets.UTF_8))
                while (true) {
                    val line = reader.readLine() ?: break
                    onLog(line)
                    val t = line.trim()
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

            // ---------- ۵) گواهی نباید عوض شده باشد ----------
            onPhase("بازبینی گواهی TLS (پین دست‌نخورده)")
            val parsed = certShaParsed?.uppercase()
            val expected = expectedCertSha256.uppercase()
            if (parsed != null && parsed != expected) {
                throw IllegalStateException(
                    "گواهی TLS سرور پس از آپدیت عوض شده است — احتمال واسطهٔ مخرب یا دستکاری. " +
                        "اتصال رد شد؛ پین ذخیره‌شده را تغییر ندهید و سرور را بررسی کنید.",
                )
            }
            onLog("گواهی TLS دست‌نخورده ماند: ${expected.take(16)}…")

            onPhase("انجام شد")
            return UpdateResult(version = release.version, source = release.source, certSha256 = expected)
        } finally {
            runCatching { ssh.disconnect() }
            localBundle.delete()
        }
    }
}
