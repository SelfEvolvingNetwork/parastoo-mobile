package network.selfevolving.parastoo.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import network.selfevolving.parastoo.mobile.core.provision.AppUpdater

/**
 * کارت به‌روزرسانی خودکار خودِ اپ — حلقهٔ آخرِ آپدیت‌ها (فرمان مالک،
 * 2026-11-09: «پیاده کن»).
 *
 * باز شدن داشبورد = بررسی کانال انتشار خود اپ؛ نسخهٔ تازه‌تر؟ دانلود جریانی
 * با صحت دو-کاناله (مانیفست main + sha256 کنار APK) و نصب با تنها یک تأیید
 * سیستم اندروید — نصبِ کاملاً بی‌صدا خارج از استور ممنوعیت امنیتی اندروید
 * است و این نزدیک‌ترین معادل امن آن است.
 */
@Composable
fun AppAutoUpdateCard() {
    val context = LocalContext.current
    val st = AppUpdater.state.collectAsState().value
    val currentName = AppUpdater.currentVersionName(context)

    // خودکار: با هر باز شدن داشبورد یک بررسی (خود شیء، فاصلهٔ حداقلی دارد)
    LaunchedEffect(Unit) { AppUpdater.check(context) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    when (st) {
                        is AppUpdater.State.Ready, is AppUpdater.State.UpToDate -> Icons.Rounded.CheckCircle
                        is AppUpdater.State.Available, is AppUpdater.State.Downloading -> Icons.Rounded.CloudDownload
                        is AppUpdater.State.Failed -> Icons.Rounded.ErrorOutline
                        else -> Icons.Rounded.SystemUpdateAlt
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = when (st) {
                        is AppUpdater.State.Ready, is AppUpdater.State.UpToDate -> MaterialTheme.colorScheme.primary
                        is AppUpdater.State.Failed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("به‌روزرسانی خودکار اپ", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when (st) {
                            is AppUpdater.State.Idle -> "اپ v$currentName — بررسی کانال انتشار…"
                            is AppUpdater.State.Checking -> "در حال بررسی کانال انتشار گیت‌هاب…"
                            is AppUpdater.State.UpToDate -> "اپ v${st.currentName} به‌روز است — همین‌طور خودکار دنبال می‌شود"
                            is AppUpdater.State.Available ->
                                "نسخهٔ v${st.release.versionName} آماده است — دانلود با یک لمس"
                            is AppUpdater.State.Downloading ->
                                "دانلود v${st.release.versionName}… " +
                                    String.format(Locale.US, "%.1f", st.bytesRead / 1048576.0) + " مگابایت"
                            is AppUpdater.State.Ready ->
                                "v${st.release.versionName} سالم است (sha256 دو-کاناله تأیید شد) — آمادهٔ نصب"
                            is AppUpdater.State.NeedsInstallPermission ->
                                "بسته آماده است؛ برای نصب، یک بار اجازهٔ «نصب از این اپ» لازم است"
                            is AppUpdater.State.Failed -> st.message
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (st) {
                            is AppUpdater.State.Ready, is AppUpdater.State.UpToDate -> MaterialTheme.colorScheme.primary
                            is AppUpdater.State.Failed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when (st) {
                    is AppUpdater.State.Checking, is AppUpdater.State.Downloading ->
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else -> {}
                }
            }

            when (st) {
                is AppUpdater.State.Available -> {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { AppUpdater.download(context) }) {
                            Text("دانلود v${st.release.versionName}")
                        }
                    }
                }
                is AppUpdater.State.Ready, is AppUpdater.State.NeedsInstallPermission -> {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            if (AppUpdater.canInstall(context)) {
                                AppUpdater.install(context)
                            } else {
                                AppUpdater.requestInstallPermission(context)
                            }
                        }) {
                            Text(
                                if (st is AppUpdater.State.Ready) "نصب (یک تأیید اندروید)"
                                else "فعال‌کردن اجازه و نصب",
                            )
                        }
                    }
                }
                is AppUpdater.State.Failed -> {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { AppUpdater.check(context, force = true) }) {
                        Text("تلاش دوباره")
                    }
                }
                else -> {}
            }
        }
    }
}
