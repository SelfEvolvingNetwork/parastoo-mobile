package network.selfevolving.parastoo.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.selfevolving.parastoo.mobile.core.model.HostStatus
import network.selfevolving.parastoo.mobile.ui.components.BackTopBar
import network.selfevolving.parastoo.mobile.ui.components.ConfirmDialog
import network.selfevolving.parastoo.mobile.ui.components.StatRow
import network.selfevolving.parastoo.mobile.ui.components.rememberSession

/** داشبورد سرور — وضعیت زندهٔ هاست + وضعیت قلمرو عامل + کلید توقف */
@Composable
fun DashboardScreen(
    serverId: Long,
    onMonitor: () -> Unit,
    onAgent: () -> Unit,
    onBack: () -> Unit,
) {
    val session = rememberSession(serverId)
    var status by remember { mutableStateOf<HostStatus?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var pauseDialog by remember { mutableStateOf<Boolean?>(null) } // null بسته؛ true/false هدف
    val scope = rememberCoroutineScope()

    fun refresh(api: network.selfevolving.parastoo.mobile.core.net.AccessAgentApi) {
        scope.launch {
            loading = true
            try {
                status = withContext(Dispatchers.IO) { api.status() }
                error = null
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(session) {
        val api = session?.api ?: return@LaunchedEffect
        while (true) {
            loading = true
            try {
                status = withContext(Dispatchers.IO) { api.status() }
                error = null
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
            delay(15000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        BackTopBar(
            title = session?.profile?.name ?: "سرور",
            subtitle = session?.profile?.url,
            onBack = onBack,
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val s = status
            if (s == null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.width(28.dp).height(28.dp))
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            error ?: "در حال گرفتن وضعیت…",
                            color = if (error != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (error != null) {
                            Spacer(Modifier.height(8.dp))
                            session?.api?.let { TextButton(onClick = { refresh(it) }) { Text("تلاش دوباره") } }
                        }
                    }
                }
            } else {
                StatRow(
                    "میزبان" to s.hostname,
                    "نسخهٔ پل" to ("v" + s.version),
                )
                StatRow(
                    "دیسک آزاد" to (s.diskFreeGb?.let { "%.1f گیگابایت".format(it) } ?: "—"),
                    "رم آزاد" to (s.memAvailMb?.let { "${it / 1024} از ${s.memTotalMb?.div(1024)} گیگ" } ?: "—"),
                )
                StatRow(
                    "آپ‌تایم سرور" to formatUptime(s.uptimeS),
                    "پرستو" to (s.parastooInstallPath.ifEmpty { "نصب نشده" }),
                )
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("قلمرو عامل هوش مصنوعی", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    when {
                                        !s.agentRealmEnabled -> "غیرفعال (توکن عامل وجود ندارد)"
                                        s.agentRealmPaused -> "موقفاً متوقف (کلید توقف)"
                                        else -> "فعال",
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        s.agentRealmPaused -> MaterialTheme.colorScheme.tertiary
                                        s.agentRealmEnabled -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            Switch(
                                checked = s.agentRealmPaused,
                                onCheckedChange = { pauseDialog = !s.agentRealmPaused },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = onMonitor,
                                label = { Text("مانیتور زنده") },
                                leadingIcon = { Icon(Icons.Filled.Monitor, null, Modifier.height(18.dp)) },
                            )
                            AssistChip(
                                onClick = onAgent,
                                label = { Text("ایجنت و بلوک تحویل") },
                                leadingIcon = { Icon(Icons.Filled.VpnKey, null, Modifier.height(18.dp)) },
                            )
                        }
                    }
                }
                Text(
                    "وضعیت هر ۱۵ ثانیه خودکار تازه می‌شود.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    pauseDialog?.let { target ->
        PauseReasonDialog(
            title = if (target) "توقف قلمرو عامل" else "ادامهٔ کار عامل",
            body = if (target)
                "بلافاصله همهٔ درخواست‌های عامل (exec/status/token) رد می‌شوند؛ کار جاری ممکن است وسط بماند. ادمین و سلامت پل دست‌نخورده‌اند و این حالت پس از ری‌استارت هم برقرار می‌ماند."
            else "عامل از همین لحظه دوباره می‌تواند به سرور دستور بفرستد.",
            confirmText = if (target) "متوقف کن" else "ادامه بده",
            onConfirm = { reason ->
                pauseDialog = null
                val api = session?.api ?: return@PauseReasonDialog
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { api.pauseRealm(target, reason) }
                        refresh(api)
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
            onDismiss = { pauseDialog = null },
        )
    }
}

@Composable
private fun PauseReasonDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(body, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("دلیل (اختیاری — به عامل نمایش داده می‌شود)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private fun formatUptime(s: Long): String {
    val d = s / 86400
    val h = (s % 86400) / 3600
    val m = (s % 3600) / 60
    return when {
        d > 0 -> "$d روز و $h ساعت"
        h > 0 -> "$h ساعت و $m دقیقه"
        else -> "$m دقیقه"
    }
}
