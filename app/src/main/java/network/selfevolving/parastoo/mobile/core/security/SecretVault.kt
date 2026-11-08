package network.selfevolving.parastoo.mobile.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * گاوصندوق محلی: توکن‌های ادمین/عامل هر سرور با AES256-GCM (کلید در Android
 * Keystore سخت‌افزاری) رمزنگاری و ذخیره می‌شوند. هیچ توکنی در Room، لاگ،
 * کلایپورد پایدار یا هر فایل دیگری نمی‌نشیند.
 *
 * گیت ورود، قفل گوشی است (BiometricGate) — یعنی بدون قفل‌گشایی دستگاه،
 * محتوای گاوصندوق در UI هرگز رندر نمی‌شود.
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

    fun deleteAll(context: Context, serverId: Long) {
        prefs(context).edit()
            .remove("admin_$serverId")
            .remove("agent_$serverId")
            .apply()
    }
}
