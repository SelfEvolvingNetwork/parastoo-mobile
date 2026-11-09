package network.selfevolving.parastoo.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import network.selfevolving.parastoo.mobile.core.appcore.AppCoreUpdater
import network.selfevolving.parastoo.mobile.core.provision.AppUpdater
import network.selfevolving.parastoo.mobile.ui.components.BackTopBar
import network.selfevolving.parastoo.mobile.ui.components.DetailRow
import network.selfevolving.parastoo.mobile.ui.components.SectionLabel

/**
 * تنظیمات اپ — بازنگری چیدمان (فرمان مالک 2026-11-09): کارت «به‌روزرسانی
 * خودکار اپ» از داشبوردِ سرور به خانهٔ درستش — تنظیمات خود اپ — منتقل شد.
 * داشبورد جای به‌روزرسانی اپ نیست؛ اپ یک موجودیت کلی است نه متعلق به یک سرور.
 * این صفحه مستقل از سرورهاست: نگهداری خود اپ + قراردادهای امنیتی + درباره.
 */
@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember { AppUpdater.currentVersionName(context) }
    val versionCode = remember { AppUpdater.currentVersionCode(context) }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(
            title = "تنظیمات اپ",
            subtitle = "نگهداری خود اپ — بی‌ربط به سرورها",
            onBack = onBack,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("نگهداری")

            // به‌روزرسانی خودکار خود اپ — بررسی در شروع اپ هم بی‌صدا ادامه دارد
            AppAutoUpdateCard()

            // هستهٔ اپ — کدِ OTA بی‌نصب (معماری بدنه ثابت + هسته؛ فرمان مالک)
            AppCoreCard()

            SectionLabel("امنیت")

            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SecurityRow(
                        icon = Icons.Rounded.Fingerprint,
                        title = "ورود با قفل گوشی",
                        body = "باز شدن اپ فقط با بیومتریک/PIN دستگاه؛ توکن‌ها پشت این قفل‌اند.",
                    )
                    SecurityRow(
                        icon = Icons.Rounded.Shield,
                        title = "پین TLS برای هر سرور",
                        body = "هر اتصال فقط با همان گواهی پین‌شده باز می‌شود؛ تغییر گواهی = توقف اتصال.",
                    )
                    SecurityRow(
                        icon = Icons.Rounded.Lock,
                        title = "گاوصندوق رمزنگاری‌شده",
                        body = "توکن‌ها و رمزها با کلید سخت‌افزاری همین گوشی (Android Keystore) قفل می‌شوند.",
                    )
                    SecurityRow(
                        icon = Icons.Rounded.PrivacyTip,
                        title = "بدون تلمتری و بدون ابر ثالث",
                        body = "تنها کانال‌های شبکهٔ اپ: پلِ خودتان و کانال انتشار گیت‌هاب برای به‌روزرسانی.",
                    )
                }
            }

            SectionLabel("درباره")

            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DetailRow("نسخه", "v$versionName (versionCode $versionCode)")
                    DetailRow(
                        "هستهٔ اپ",
                        buildString {
                            val rev = AppCoreUpdater.activeRevision(context)
                            append(if (rev > 0) "rev$rev" else "—")
                            val src = AppCoreUpdater.activeSource(context)
                            if (src.isNotBlank()) append(" از $src")
                        },
                    )
                    DetailRow("منبع کد", "github.com/SelfEvolvingNetwork/parastoo-mobile", mono = true)
                    DetailRow("مجوز", "MIT — Copyright © 2026 SelfEvolvingNetwork")
                    Text(
                        "پرستو موبایل اپ سمت ادمین است: توکن ادمین هرگز به عامل داده نمی‌شود و عامل حتی وجود این اپ را نمی‌بیند.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** یک سطر قرارداد امنیتی: آیکن + عنوان + یک جملهٔ توضیح */
@Composable
private fun SecurityRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
