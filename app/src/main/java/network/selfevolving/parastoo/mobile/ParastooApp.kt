package network.selfevolving.parastoo.mobile

import android.app.Application
import network.selfevolving.parastoo.mobile.core.config.CoreConfigStore
import network.selfevolving.parastoo.mobile.core.data.Db
import network.selfevolving.parastoo.mobile.core.security.BcFix

class ParastooApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // رفع BC قدیمی اندروید پیش از هر چیز — حتی پیش از لود هسته (درس r10).
        // بخش sshj-آگاه (CryptoFix) سمت هسته است و خودش پیش از هر SSHClient
        // صدا زده می‌شود؛ اینجا فقط زنجیرهٔ JCE سالم می‌شود.
        BcFix.ensure()
        Db.init(this)
        // هستهٔ هات داده‌ای — باندل/کش پیش از هر ساخت api (v0.4.12)
        CoreConfigStore.init(this)
    }
}
