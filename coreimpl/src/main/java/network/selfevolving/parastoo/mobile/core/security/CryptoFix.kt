package network.selfevolving.parastoo.mobile.core.security

import android.util.Log
import net.schmizz.sshj.common.SecurityUtils
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
 *  ۱) بخش BC در «BcFix» (:foundation — بدون وابستگی sshj) انجام می‌شود تا بدنه
 *     هم پیش از لود هسته بتواند زنجیره را سالم کند؛ اینجا فقط بخشِ ویژهٔ sshj
 *     اضافه می‌شود.
 *  ۲) به sshj می‌گوییم خودش BC را ثبت نکند (setRegisterBouncyCastle(false)) تا از
 *     همان زنجیرهٔ پیش‌فرض استفاده کند که حالا BC کامل دارد.
 *
 * این کار باید قبل از هر استفاده از sshj انجام شود؛ ServerProvisioner و
 * BridgeUpdater پیش از ساخت SSHClient صدا می‌زنند (idempotent).
 */
object CryptoFix {

    private const val TAG = "ParastooCryptoFix"

    @Volatile
    private var done = false

    fun ensure() {
        if (done) return
        synchronized(this) {
            if (done) return
            // ۱) BC کامل در زنجیره (BcFix بدنه هم در onCreate صدا می‌زند — idempotent)
            BcFix.ensure()
            try {
                // ۲) sshj از زنجیرهٔ پیش‌فرض JCE استفاده کند (که حالا BC کامل دارد) —
                // وگرنه خودش «BC» را ثبت می‌کند و اگر اپ دیر رسیده باشد دوباره به
                // BC قدیمی برمی‌گردد.
                SecurityUtils.setRegisterBouncyCastle(false)
                SecurityUtils.setSecurityProvider(null)
            } catch (t: Throwable) {
                Log.w(TAG, "sshj provider config failed: ${t.message}")
            }
            done = true
            Log.i(TAG, "CryptoFix applied — X25519 available: ${BcFix.x25519Available()}")
        }
    }

    /** آیا تبادل کلید X25519 در این دستگاه در دسترس است؟ (برای فالبک KEX) */
    fun x25519Available(): Boolean = BcFix.x25519Available()
}
