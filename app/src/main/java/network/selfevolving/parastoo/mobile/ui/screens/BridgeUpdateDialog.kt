package network.selfevolving.parastoo.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.selfevolving.parastoo.mobile.core.provision.BridgeUpdater
import network.selfevolving.parastoo.mobile.ui.components.ServerSession

/**
 * به‌روزرسانی پل (access-agent) مستقیماً از کانال انتشار گیت‌هاب — قرارداد §۸.
 * رمز root فقط برای همین اجرا پرسیده و هیچ‌گاه ذخیره نمی‌شود؛ توکن‌ها و گواهی
 * سرور دست‌نخورده می‌مانند (مسیر ارتقای install.sh). گواهیِ عوض‌شده = رد.
 */
@Composable
fun BridgeUpdateDialog(
    session: ServerSession,
    currentVersion: String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var sshPort by remember { mutableStateOf("22") }
    var password by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("") }
    var logs by remember { mutableStateOf(listOf<String>()) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun log(line: String) {
        logs = (logs + line).takeLast(200)
    }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text("به‌روزرسانی پل") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!running && result == null) {
                    Text(
                        "آخرین بستهٔ پل مستقیماً از کانال انتشار گیت‌هاب دانلود و روی سرور نصب می‌شود؛ توکن‌ها، گواهی و داده‌ها دست‌نخورده می‌مانند. برای اجرا رمز root سرور لازم است.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = sshPort,
                        onValueChange = { sshPort = it },
                        label = { Text("پورت SSH") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("رمز root سرور") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                if (running) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(phase, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        logs.takeLast(10).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                result?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !running && password.isNotBlank() && result == null,
                onClick = {
                    running = true
                    error = null
                    scope.launch {
                        try {
                            val host = android.net.Uri.parse(session.profile.url).host ?: ""
                            val res = withContext(Dispatchers.IO) {
                                BridgeUpdater().update(
                                    host = host,
                                    sshPort = sshPort.trim().toIntOrNull() ?: 0,
                                    password = password,
                                    currentVersion = currentVersion,
                                    expectedCertSha256 = session.profile.certSha256,
                                    cacheDir = File(context.cacheDir, "bridge-update"),
                                    onPhase = { p -> phase = p },
                                    onLog = ::log,
                                )
                            }
                            val st = withContext(Dispatchers.IO) { session.api.status() }
                            if (st.version != res.version) {
                                error = "هشدار: نسخهٔ گزارش‌شدهٔ پل (v${st.version}) با بستهٔ نصب‌شده (v${res.version}) نمی‌خواند."
                            }
                            result = "پل روی v${st.version} است (بسته: v${res.version} — ${res.source}). توکن‌ها و گواهی دست‌نخورده ماندند."
                        } catch (e: Exception) {
                            error = e.message ?: e.javaClass.simpleName
                        } finally {
                            running = false
                        }
                    }
                },
            ) { Text("به‌روزرسانی") }
        },
        dismissButton = {
            TextButton(enabled = !running, onClick = onDismiss) {
                Text(if (result == null) "انصراف" else "بستن")
            }
        },
    )
}
