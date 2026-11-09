package network.selfevolving.parastoo.mobile.core.provision

import android.content.Context
import network.selfevolving.parastoo.mobile.core.data.ServerProfileEntity
import network.selfevolving.parastoo.mobile.core.security.SecretVault
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * به‌روزرسانی خودکار پل — فرمان مالک (2026-11-09):
 * «بروزرسانی پرستو و پل باید اتوماتیک باشد و نباید پرسیده شود.»
 *
 * این شیء وقتی داشبورد سرور باز است، نسخهٔ کانال انتشار گیت‌هاب را با نسخهٔ
 * نصب‌شده مقایسه می‌کند و اگر بستهٔ تازه‌تری هست، بدون هیچ پرسشی با رمزِ
 * ذخیره‌شده در گاوصندوق نصبش می‌کند (سرور هرگز به‌طور خودکار downgrade
 * نمی‌شود و توکن‌ها/گواهی در مسیر ارتقا حفظ می‌مانند — قرارداد §۸).
 *
 * تنها حالتِ «پرسیدن»: رمز ذخیره‌شده پذیرفته نشود (مثلاً مالک رمز سرور را عوض
 * کرده باشد) یا هنوز رمزی ذخیره نشده باشد — آن‌وقت یک بار رمز پرسیده می‌شود،
 * در گاوصندوق می‌ماند و از آن پس همه‌چیز باز خودکار است.
 *
 * به‌روزرسانی خودکار «پرستو» سمت سرور انجام می‌شود (تایمر auto-update که از
 * فرمان 2026-11-09 در نصب پیش‌فرض مسلح می‌شود)؛ این کلاس فقط پل را می‌کشد،
 * چون پل تنها از کانال SSH گوشی قابل ارتقا است.
 */
object AutoUpdater {

    sealed class State {
        /** هیچ اجرایی در جریان نیست / هنوز بررسی نشده */
        data object Idle : State()

        /** در حال مقایسهٔ نسخه با کانال انتشار */
        data class Checking(val current: String) : State()

        /** پل همان آخرین نسخهٔ کانال را دارد */
        data class UpToDate(val current: String) : State()

        /** بستهٔ تازه‌تر پیدا شد و در حال نصب است */
        data class Updating(val from: String, val to: String, val phase: String, val lastLog: String) : State()

        /** نصب کامل شد */
        data class Done(val from: String, val to: String) : State()

        /** ناموفق — needsPassword یعنی تنها مانع، رمز است (یک بار پرسیده شود) */
        data class Failed(val message: String, val needsPassword: Boolean) : State()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _states = MutableStateFlow<Map<Long, State>>(emptyMap())
    val states: StateFlow<Map<Long, State>> = _states.asStateFlow()

    private val inFlight = ConcurrentHashMap.newKeySet<Long>()

    fun state(serverId: Long): State = _states.value[serverId] ?: State.Idle

    private fun put(serverId: Long, s: State) {
        _states.value = _states.value + (serverId to s)
    }

    /**
     * اگر بستهٔ تازه‌تری در کانال انتشار باشد، بی‌سروصدا نصبش می‌کند.
     * فراخوانی‌های هم‌زمان برای یک سرور یکی می‌شوند (in-flight guard).
     */
    fun checkAndMaybeUpdate(
        context: Context,
        serverId: Long,
        profile: ServerProfileEntity,
        currentVersion: String,
    ) {
        if (currentVersion.isBlank()) return
        if (!inFlight.add(serverId)) return
        scope.launch {
            try {
                run(context, serverId, profile, currentVersion)
            } catch (e: Exception) {
                put(serverId, State.Failed(e.message ?: e.javaClass.simpleName, needsPassword = false))
            } finally {
                inFlight.remove(serverId)
            }
        }
    }

    private suspend fun run(
        context: Context,
        serverId: Long,
        profile: ServerProfileEntity,
        currentVersion: String,
    ) {
        // ---------- ۱) رمز از گاوصندوق — اگر نیست، همین یک بار پرسیده می‌شود ----------
        val password = SecretVault.getSshPassword(context, serverId)
        if (password.isBlank()) {
            put(serverId, State.Failed("برای نصب خودکار، رمز سرور باید یک بار در اپ ذخیره شود.", needsPassword = true))
            return
        }

        put(serverId, State.Checking(currentVersion))

        // ---------- ۲) کانال انتشار — شکست شبکه خطا نیست، فقط «الان نشد» ----------
        val release = try {
            BridgeReleaseClient.fetchLatest()
        } catch (e: Exception) {
            put(serverId, State.Failed("کانال انتشار گیت‌هاب در دسترس نیست: ${e.message}", needsPassword = false))
            return
        }

        // ---------- ۳) فقط تازه‌تر نصب می‌شود — downgrade یا هم‌نسخه = هیچ‌کاری ----------
        if (release.version == currentVersion ||
            !BridgeReleaseClient.isAtLeast(release.version, currentVersion)
        ) {
            put(serverId, State.UpToDate(currentVersion))
            return
        }

        // ---------- ۴) نصب بی‌پرسش با رمز گاوصندوق ----------
        var lastLog = ""
        put(serverId, State.Updating(currentVersion, release.version, "شروع نصب", ""))
        try {
            val res = BridgeUpdater().update(
                host = profile.url.extractHost(),
                sshPort = SecretVault.getSshPort(context, serverId),
                password = password,
                currentVersion = currentVersion,
                expectedCertSha256 = profile.certSha256,
                cacheDir = File(context.cacheDir, "bridge-autoupdate"),
                expectedHostKeyFp = SecretVault.getSshHostKeyFp(context, serverId),
                onPhase = { p -> put(serverId, State.Updating(currentVersion, release.version, p, lastLog)) },
                onLog = { line ->
                    lastLog = line
                    put(serverId, State.Updating(currentVersion, release.version, "", lastLog))
                },
            )
            // TOFU اولین به‌روزرسانیِ این نسخهٔ اپ — پین میزبان را ثبت کن تا بعدی‌ها سخت‌گیر شوند
            if (res.sshHostKeyFp.isNotBlank() &&
                SecretVault.getSshHostKeyFp(context, serverId).isBlank()
            ) {
                SecretVault.saveSshHostKeyFp(context, serverId, res.sshHostKeyFp)
            }
            put(serverId, State.Done(from = currentVersion, to = res.version))
        } catch (e: Exception) {
            val authFail = e is net.schmizz.sshj.userauth.UserAuthException ||
                (e.message ?: "").contains("Auth fail", ignoreCase = true)
            if (authFail) {
                // رمز دیگر معتبر است نیست — پاک می‌شود تا فقط یک بار دوباره پرسیده شود
                SecretVault.deleteSshPassword(context, serverId)
                put(
                    serverId,
                    State.Failed(
                        "رمز ذخیره‌شده پذیرفته نشد (احتمالاً رمز سرور عوض شده). یک بار رمز تازه را وارد کنید؛ از آن پس باز خودکار می‌شود.",
                        needsPassword = true,
                    ),
                )
            } else {
                put(serverId, State.Failed(e.message ?: e.javaClass.simpleName, needsPassword = false))
            }
        }
    }

    private fun String.extractHost(): String =
        android.net.Uri.parse(this).host ?: ""
}
