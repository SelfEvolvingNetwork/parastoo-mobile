package network.selfevolving.parastoo.mobile.core.appcore

import androidx.compose.runtime.Composable
import network.selfevolving.parastoo.mobile.core.contract.AppCoreEntry
import network.selfevolving.parastoo.mobile.ui.AppRoot

/**
 * نقطهٔ ورود هستهٔ پرستو موبایل — تنها کلاسی که بدنه با نامِ قراردادی
 * (entry_class در مانیفست بسته) می‌شناسد.
 *
 * تم/فونت/RTL و گیت قفل گوشی کار بدنه است؛ بدنه این App() را داخل
 * ParastooTheme صدا می‌زند. هر چیز دیگری که هسته از بدنه بخواهد باید از
 * :foundation بیاید (قرارداد AppCore) — این کلاس عمداً هیچ کلاس بدنه‌ای را
 * نمی‌شناسد.
 */
class ParastooCoreEntry : AppCoreEntry {

    @Composable
    override fun App() {
        AppRoot()
    }
}
