package network.selfevolving.parastoo.mobile

import android.app.Application
import network.selfevolving.parastoo.mobile.core.data.Db
import network.selfevolving.parastoo.mobile.core.security.CryptoFix

class ParastooApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // رفع BC قدیمی اندروید پیش از هر کار رمزنگاری (sshj/X25519) — r10
        CryptoFix.ensure()
        Db.init(this)
    }
}
