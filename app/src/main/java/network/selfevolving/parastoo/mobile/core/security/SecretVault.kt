package network.selfevolving.parastoo.mobile.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * گاوصندوق محلی: توکن‌های ادمین/عامل و رمز SSH هر سرور با AES256-GCM (کلید در
 * Android Keystore سخت‌افزاری) رمزنگاری و ذخیره می‌شوند. هیچ رازی در Room،
 * لاگ، کلایپورد پایدار یا هر فایل دیگری نمی‌نشیند.
 *
 * گیت ورود، قفل گوشی است (BiometricGate) — یعنی بدون قفل‌گشایی دستگاه،
 * محتوای گاوصندوق در UI هرگز رندر نمی‌شود.
 *
 * فرمان مالک (2026-11-09): «رمز سرور یک بار گرفته می‌شود و تمام — اپ امن‌ترین
 * جای نگه‌داری است؛ پرسیدن مکرر رمز، امنیت را کم و خطای انسانی را زیاد می‌کند.»
 * پس رمز SSH/پورت/اثرانگشت کلید میزبان هم همین‌جا می‌مانند تا به‌روزرسانی خودکار
 * پل و کارهای مدیریتی بدون هیچ پرسشی اجرا شوند.
 */
object SecretVault {

    private const val FILE = "parastoo_vault"

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun saveAdminToken(context: Context, serverId: Long, token: String) {
        prefs(context).edit().putString("admin_$serverId", token).apply()
    }

    fun getAdminToken(context: Context, serverId: Long): String =
        prefs(context).getString("admin_$serverId", "") ?: ""

    fun saveAgentToken(context: Context, serverId: Long, token: String) {
        prefs(context).edit().putString("agent_$serverId", token).apply()
    }

    fun getAgentToken(context: Context, serverId: Long): String =
        prefs(context).getString("agent_$serverId", "") ?: ""

    // ---------- رمز SSH — یک بار از کاربر، برای همیشه در گاوصندوق (فرمان مالک 2026-11-09) ----------

    fun saveSshPassword(context: Context, serverId: Long, password: String) {
        prefs(context).edit().putString("sshpass_$serverId", password).apply()
    }

    fun getSshPassword(context: Context, serverId: Long): String =
        prefs(context).getString("sshpass_$serverId", "") ?: ""

    /** اگر رمز دیگر پذیرفته نشد (مثلاً عوض شده) پاک می‌شود تا فقط یک بار دوباره پرسیده شود */
    fun deleteSshPassword(context: Context, serverId: Long) {
        prefs(context).edit().remove("sshpass_$serverId").apply()
    }

    fun saveSshPort(context: Context, serverId: Long, port: Int) {
        prefs(context).edit().putInt("sshport_$serverId", port).apply()
    }

    fun getSshPort(context: Context, serverId: Long): Int =
        prefs(context).getInt("sshport_$serverId", 22)

    /** اثرانگشت SHA-256 کلید میزبان SSH — TOFU در اولین نصب، پین سخت‌گیر بعد از آن */
    fun saveSshHostKeyFp(context: Context, serverId: Long, fp: String) {
        prefs(context).edit().putString("sshhostkey_$serverId", fp).apply()
    }

    fun getSshHostKeyFp(context: Context, serverId: Long): String =
        prefs(context).getString("sshhostkey_$serverId", "") ?: ""

    fun deleteAll(context: Context, serverId: Long) {
        prefs(context).edit()
            .remove("admin_$serverId")
            .remove("agent_$serverId")
            .remove("sshpass_$serverId")
            .remove("sshport_$serverId")
            .remove("sshhostkey_$serverId")
            .apply()
    }
}
