package network.selfevolving.parastoo.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import network.selfevolving.parastoo.mobile.core.appcore.AppCoreUpdater
import network.selfevolving.parastoo.mobile.ui.components.DetailRow

/**
 * کارت «هستهٔ اپ» — وضعیت کدِ OTA (فرمان مالک 2026-11-09: «بدنه ثابت یک بار
 * نصب؛ هستهٔ اصلی توسط بدنه بروز شود»).
 *
 * این کارت فقط «وضعیت صادقانه» نشان می‌دهد و یک کنش دارد: بررسی دوباره.
 * هیچ تأیید و هیچ دیالوگ اندرویدی در مسیر هست — به‌روزرسانی هسته بی‌پرسش
 * انجام می‌شود و بدنه اکتیویتی را نرم بازآرایی می‌کند. تفاوت با کارت
 * «به‌روزرسانی خودکار اپ»: آن یکی APK بدنه است (نادر و با تأیید سیستم
 * اندروید) و این یکی کدِ روزمرهٔ اپ است که بدون هیچ نصبی تازه می‌شود.
 */
@Composable
fun AppCoreCard() {
    val context = LocalContext.current
    val st = AppCoreUpdater.state.collectAsState().value
    val activeRev = remember { AppCoreUpdater.activeRevision(context) }
    val activeSource = remember { AppCoreUpdater.activeSource(context) }
    val activeVersion = remember { AppCoreUpdater.activeVersionName(context) }

    // بررسی بی‌صدا هنگام ورود به تنظیمات هم ادامه دارد (throttle داخلی خودش)
    LaunchedEffect(Unit) { AppCoreUpdater.check(context) }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Bolt,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 10.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        "هستهٔ اپ — بی‌نصب به‌روز می‌شود",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        buildString {
                            append("فعلی: ")
                            append(if (activeRev > 0) "rev$activeRev" else "لود نشده")
                            if (activeVersion.isNotBlank()) append(" (v$activeVersion)")
                            if (activeSource.isNotBlank()) append(" — از $activeSource")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { AppCoreUpdater.check(context, force = true) }) {
                    Text("بررسی دوباره")
                }
            }

            Text(
                "رابط و منطق پرستو موبایل «هسته» است و از کانال انتشار گیت‌هاب با صحت دو-کاناله می‌آید؛ بدون هیچ نصبی و بدون هیچ دیالوگی — بدنه در لحظه بازآرایی می‌شود. فقط تغییرات ساختاری (پرمیشن، کتابخانه، فونت) به APK بدنه نیاز دارند که کارت بالا را می‌گیرد.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (val s = st) {
                is AppCoreUpdater.State.Idle -> DetailRow("وضعیت", "هنوز بررسی نشده")
                is AppCoreUpdater.State.Checking ->
                    DetailRow("وضعیت", "در حال مقایسه با کانال انتشار (فعلی rev${s.activeRevision})…")
                is AppCoreUpdater.State.UpToDate ->
                    DetailRow("وضعیت", "هسته به‌روز است — آخرین rev کانال همان rev${s.activeRevision} است")
                is AppCoreUpdater.State.Downloading ->
                    DetailRow(
                        "وضعیت",
                        "در حال دانلود هستهٔ rev${s.revision} — ${(s.bytesRead / 1024)} کیلوبایت…",
                    )
                is AppCoreUpdater.State.Installed -> {
                    DetailRow(
                        "وضعیت",
                        "هستهٔ rev${s.toRevision} نصب شد — بدنه اپ را بازآرایی می‌کند (بدون هیچ نصبی)",
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "اگر نسخهٔ تازه بالا نیاید، بدنه خودش دو بار تلاش می‌کند و بعد به هستهٔ قبلی برمی‌گردد — ریسک شکست صفر است.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is AppCoreUpdater.State.Failed -> {
                    DetailRow("وضعیت", "این بررسی نشد: ${s.message}")
                    Text(
                        "هستهٔ فعلی سرِ کارش می‌ماند — شکست به‌روزرسانی هیچ اثری روی اپ ندارد.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
