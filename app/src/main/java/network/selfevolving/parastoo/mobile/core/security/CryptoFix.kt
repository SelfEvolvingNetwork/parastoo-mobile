package network.selfevolving.parastoo.mobile.core.security

import android.util.Log
import net.schmizz.sshj.common.SecurityUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyAgreement
import java.security.Provider
import java.security.Security

/**
 * رفع باگ «no such algorithm: X25519 for provider BC» (مشاهدهٔ میدانی r10، 2026-11-08):
 *
 * اندروید یک BouncyCastle قدیمی و سترون‌شده (نسخهٔ ~1.49 که گوگل بیشتر الگوریتم‌هایش را
 * حذف کرده) با نام «BC» از پیش در زنجیرهٔ JCE دارد. sshj در SecurityUtils.register()
 * می‌بیند «BC» از قبل هست، پس نسخهٔ کاملِ همراه اپ را در Security ثبت نمی‌کند، ولی
 * securityProvider را «BC» می‌گذارد؛ نتیجه: هر درخواست X25519 (تبادل کلید
 * curve25519-sha256) به BC قدیمیِ سیستم‌عامل می‌رسد که X25519 ندارد → نصب شکست می‌خورد.
 *
 * راه‌حل:
 *  ۱) «BC» قدیمی (version < 60) حذف و BC کاملِ همراه اپ در زنجیره ثبت می‌شود —
 *     در «انتهای» زنجیره، تا الگوریتم‌های رایع روی Conscrypt/SunJCE سریع بمانند و
 *     فقط کمبودها (X25519/XDH/Ed25519) از BC کامل پر شود.
 *  ۲) به sshj می‌گوییم خودش BC را ثبت نکند (setRegisterBouncyCastle(false)) تا از
 *     همان زنجیرهٔ پیش‌فرض استفاده کند که حالا BC کامل دارد.
 *
 * این کار باید قبل از هر استفاده از sshj انجام شود؛ ParastooApp.onCreate آن را صدا می‌زند
 * و ServerProvisioner هم برای اطمینان پیش از ساخت SSHClient دوباره صدا می‌زند (idempotent).
 */
object CryptoFix {

    private const val TAG = "ParastooCryptoFix"

    /** BC کامل jdk18on نسخهٔ 1.78 گزارش version ≈ 1.78 می‌دهد؛ BC اندروید ~1.49 */
    private const val FULL_BC_MIN_VERSION = 60.0

    @Volatile
    private var done = false

    fun ensure() {
        if (done) return
        synchronized(this) {
            if (done) return
            try {
                val existing: Provider? = Security.getProvider("BC")
                if (existing != null && existing.version < FULL_BC_MIN_VERSION) {
                    Security.removeProvider("BC")
                    Log.i(TAG, "BC قدیمی اندروید (v${existing.version}) حذف شد")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "removeProvider(BC) failed: ${t.message}")
            }
            try {
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                    Security.addProvider(BouncyCastleProvider())
                    Log.i(TAG, "BouncyCastle کامل ثبت شد")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "addProvider(BC) failed: ${t.message}")
            }
            try {
                // sshj از زنجیرهٔ پیش‌فرض JCE استفاده کند (که حالا BC کامل دارد) —
                // وگرنه خودش «BC» را ثبت می‌کند و اگر اپ دیر رسیده باشد دوباره به
                // BC قدیمی برمی‌گردد.
                SecurityUtils.setRegisterBouncyCastle(false)
                SecurityUtils.setSecurityProvider(null)
            } catch (t: Throwable) {
                Log.w(TAG, "sshj provider config failed: ${t.message}")
            }
            done = true
            Log.i(TAG, "CryptoFix applied — X25519 available: ${x25519Available()}")
        }
    }

    /** آیا تبادل کلید X25519 در این دستگاه در دسترس است؟ (برای فالبک KEX) */
    fun x25519Available(): Boolean = try {
        KeyAgreement.getInstance("X25519")
        true
    } catch (t: Throwable) {
        false
    }
}
