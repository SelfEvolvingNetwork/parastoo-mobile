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
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import network.selfevolving.parastoo.mobile.core.provision.AutoUpdater
import network.selfevolving.parastoo.mobile.core.security.SecretVault
import network.selfevolving.parastoo.mobile.ui.components.ServerSession

/**
 * کارت به‌روزرسانی خودکار پل — فرمان مالک (2026-11-09):
 * «بروزرسانی پل باید اتوماتیک باشد و نباید پرسیده شود.»
 *
 * باز شدن داشبورد = بررسی کانال انتشار؛ نسخهٔ تازه‌تر؟ نصب بی‌پرسش با رمز
 * گاوصندوق. تنها حالت پرسیدن: رمز ذخیره نشده باشد یا دیگر پذیرفته نشود —
 * یک بار پرسیده، در گاوصندوق ماند و از آن پس باز خودکار.
 */
@Composable
fun BridgeAutoUpdateCard(
    session: ServerSession,
    currentVersion: String,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val serverId = session.profile.id
    val allStates by AutoUpdater.states.collectAsState()
    val state = allStates[serverId] ?: AutoUpdater.State.Idle
    var passwordDialog by remember { mutableStateOf(false) }

    // خودکار: با هر باز شدن داشبورد (و پس از هر تغییر نسخهٔ گزارش‌شده) یک بررسی
    LaunchedEffect(serverId, currentVersion) {
        AutoUpdater.checkAndMaybeUpdate(context, serverId, session.profile, currentVersion)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    when (state) {
                        is AutoUpdater.State.Done -> Icons.Rounded.CheckCircle
                        is AutoUpdater.State.Updating -> Icons.Rounded.CloudDownload
                        is AutoUpdater.State.Failed -> Icons.Rounded.ErrorOutline
                        else -> Icons.Rounded.SystemUpdateAlt
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = when (state) {
                        is AutoUpdater.State.Done, is AutoUpdater.State.UpToDate -> MaterialTheme.colorScheme.primary
                        is AutoUpdater.State.Failed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("به‌روزرسانی خودکار پل", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when (state) {
                            is AutoUpdater.State.Idle -> "پل v$currentVersion — بررسی خودکار نسخهٔ تازه"
                            is AutoUpdater.State.Checking -> "در حال بررسی کانال انتشار گیت‌هاب…"
                            is AutoUpdater.State.UpToDate -> "پل v${state.current} به‌روز است — بدون هیچ پرسشی دنبال می‌شود"
                            is AutoUpdater.State.Updating ->
                                "نصب v${state.to} روی سرور (از v${state.from})${if (state.phase.isBlank()) "" else " — ${state.phase}"}"
                            is AutoUpdater.State.Done -> "پل از v${state.from} به v${state.to} رسید — همه‌چیز خودکار انجام شد"
                            is AutoUpdater.State.Failed -> state.message
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (state) {
                            is AutoUpdater.State.Done, is AutoUpdater.State.UpToDate -> MaterialTheme.colorScheme.primary
                            is AutoUpdater.State.Failed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when (state) {
                    is AutoUpdater.State.Checking, is AutoUpdater.State.Updating ->
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else -> {}
                }
            }

            // آخرین خط لاگ نصب — شفافیت کامل بدون شلوغی
            val lastLog = (state as? AutoUpdater.State.Updating)?.lastLog.orEmpty()
            if (lastLog.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    lastLog,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (state) {
                is AutoUpdater.State.Failed -> {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (state.needsPassword) {
                            TextButton(onClick = { passwordDialog = true }) {
                                Icon(Icons.Rounded.Sync, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("ذخیرهٔ رمز در اپ")
                            }
                        } else {
                            TextButton(onClick = {
                                AutoUpdater.checkAndMaybeUpdate(
                                    context, serverId, session.profile, currentVersion,
                                )
                            }) { Text("تلاش دوباره") }
                        }
                    }
                }
                is AutoUpdater.State.Done -> {
                    LaunchedEffect(state.to) { onRefresh() }
                }
                else -> {}
            }
        }
    }

    if (passwordDialog) {
        OneTimePasswordDialog(
            session = session,
            onSaved = {
                passwordDialog = false
                AutoUpdater.checkAndMaybeUpdate(context, serverId, session.profile, currentVersion)
            },
            onDismiss = { passwordDialog = false },
        )
    }
}

/** تنها جای اپ که بعد از نصب اولیه ممکن است رمز بپرسد — و فقط یک بار */
@Composable
private fun OneTimePasswordDialog(
    session: ServerSession,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val serverId = session.profile.id
    var password by remember { mutableStateOf("") }
    var sshPort by remember { mutableStateOf(SecretVault.getSshPort(context, serverId).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("رمز سرور — فقط همین یک بار") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "به‌روزرسانی خودکار پل به رمز سرور نیاز دارد. رمز با AES-256 روی همین گوشی (کلید سخت‌افزاری) قفل می‌شود، هیچ‌جا نمایش یا لاگ نمی‌شود و از این پس همه‌چیز بی‌پرسش انجام می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("رمز root سرور") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = sshPort,
                    onValueChange = { sshPort = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("پورت SSH") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.isNotBlank() && (sshPort.toIntOrNull() ?: 0) in 1..65535,
                onClick = {
                    SecretVault.saveSshPassword(context, serverId, password)
                    SecretVault.saveSshPort(context, serverId, sshPort.toIntOrNull() ?: 22)
                    onSaved()
                },
            ) { Text("ذخیره و ادامه") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بعداً") } },
    )
}
