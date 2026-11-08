package network.selfevolving.parastoo.mobile.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import network.selfevolving.parastoo.mobile.R

/**
 * گیت ورود با قفل گوشی — قرارداد مالک: «لاگین اول با اطلاعات سرور،
 * دفعات بعد با قفل گوشی». درصورت نبود قفل دستگاه، اپ بدون گیت باز می‌شود و
 * خود کاربر باید در تنظیمات گوشی قفل بگذارد (در UI هشدار داده می‌شود).
 */
object BiometricGate {

    fun available(activity: FragmentActivity): Boolean {
        val bm = BiometricManager.from(activity)
        val auths = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return bm.canAuthenticate(auths) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun unlock(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    onError(errString.toString())
                }
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.unlock_title))
            .setSubtitle(activity.getString(R.string.unlock_subtitle))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }
}
