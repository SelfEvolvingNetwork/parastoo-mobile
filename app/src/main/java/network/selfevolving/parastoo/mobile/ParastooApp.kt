package network.selfevolving.parastoo.mobile

import android.app.Application
import network.selfevolving.parastoo.mobile.core.data.Db

class ParastooApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Db.init(this)
    }
}
