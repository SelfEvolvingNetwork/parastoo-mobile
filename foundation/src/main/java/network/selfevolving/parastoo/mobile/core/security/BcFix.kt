package network.selfevolving.parastoo.mobile.core.security

import android.util.Log
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Provider
import java.security.Security
import javax.crypto.KeyAgreement

/**
 * ترمیم زنجیرهٔ JCE — فقط بخش BouncyCastle، بدون وابستگی به sshj.
 *
 * این تکه در :foundation زندگی می‌کند تا بدنه (ParastooApp.onCreate) پیش از
 * هر چیز — حتی پیش از لود هسته — زنجیرهٔ رمزنگاری را سالم کند؛ نسخهٔ کاملِ
 * sshj-آگاه همان منطق در CryptoFix (ماژول هسته) است که اینجا را صدا می‌زند.
 *
 * پس‌زمینه (درس میدانی r10، 2026-11-08): اندروید یک BC قدیمیِ سترون‌شده
 * (~1.49، بدون X25519) با نام «BC» در زنجیره دارد؛ این ترمیم آن را حذف و BC
 * کاملِ همراه اپ را در انتهای زنجیره ثبت می‌کند تا الگوریتم‌های رایع روی
 * Conscrypt/SunJCE سریع بمانند و فقط کمبودها (X25519/XDH/Ed25519) از BC کامل
 * پر شود. idempotent است و هر بار که لازم شد می‌توان دوباره صدا زد.
 */
object BcFix {

    private const val TAG = "ParastooBcFix"

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
            done = true
            Log.i(TAG, "BcFix applied — X25519 available: ${x25519Available()}")
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
