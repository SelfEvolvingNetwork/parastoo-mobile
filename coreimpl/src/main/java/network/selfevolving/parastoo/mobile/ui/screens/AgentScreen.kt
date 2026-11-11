package network.selfevolving.parastoo.mobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.KeyOff
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.selfevolving.parastoo.mobile.core.model.AgentTokenMeta
import network.selfevolving.parastoo.mobile.core.security.SecretVault
import network.selfevolving.parastoo.mobile.ui.components.BackTopBar
import network.selfevolving.parastoo.mobile.ui.components.CodeBlock
import network.selfevolving.parastoo.mobile.ui.components.ConfirmDialog
import network.selfevolving.parastoo.mobile.ui.components.rememberSession

/**
 * ایجنت و بلوک تحویل — ادمین اینجا توکن عامل جدید می‌سازد و «بلوک تحویل»
 * آماده را یک‌جا کپی می‌کند تا به هر عامل هوش مصنوعی دلخواه بدهد.
 */
@Composable
fun AgentScreen(serverId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val session = rememberSession(serverId)
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var meta by remember { mutableStateOf<AgentTokenMeta?>(null) }
    var rotating by remember { mutableStateOf(false) }
    var showRotateDialog by remember { mutableStateOf(false) }
    var showRevokeDialog by remember { mutableStateOf(false) }
    var handoffBlock by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pairCode by remember { mutableStateOf("") }
    var approving by remember { mutableStateOf(false) }
    var pairingResult by remember { mutableStateOf<String?>(null) }

    fun refreshMeta() {
        val api = session?.api ?: return
        scope.launch {
            try {
                meta = withContext(Dispatchers.IO) { api.agentTokenMeta() }
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    LaunchedEffect(session) { refreshMeta() }

    fun approvePairing() {
        val api = session?.api ?: return
        approving = true
        pairingResult = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { api.approveAgentPairing(pairCode) }
                pairingResult = "تأیید شد — عامل تا ۳۰ دقیقه فرصت دارد توکن موقتش را بردارد."
                pairCode = ""
                Toast.makeText(context, "عامل تأیید شد", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                pairingResult = "تأیید نشد: ${e.message}"
            } finally {
                approving = false
            }
        }
    }

    fun revokeAgentSessions() {
        val api = session?.api ?: return
        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) { api.revokeAgentSessions() }
                Toast.makeText(
                    context,
                    "نشست‌های موقت باطل شد (${res.optInt("removed")})",
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BackTopBar(
                title = "ایجنت و بلوک تحویل",
                subtitle = session?.profile?.name,
                onBack = onBack,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showRotateDialog = true },
                icon = { Icon(Icons.Rounded.VpnKey, contentDescription = null) },
                text = { Text("توکن جدید") },
                shape = RoundedCornerShape(16.dp),
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // وضعیت دسترسی عامل
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(13.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.VpnKey,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("وضعیت دسترسی عامل", style = MaterialTheme.typography.titleMedium)
                        if (meta == null) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (meta!!.set) "توکن عامل فعال است" else "هیچ توکن عاملی ست نشده (قلمرو بسته)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (meta!!.set) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (meta!!.label.isNotBlank()) {
                                Text(
                                    "برچسب: ${meta!!.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (meta!!.rotatedAt.isNotBlank()) {
                                Text(
                                    "آخرین تولید: ${meta!!.rotatedAt.take(19).replace('T', ' ')}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Text(
                "هر «توکن جدید»، توکن قبلی را باطل می‌کند — برای تعویض عامل یا قطع دسترسی یک عامل فراری همین کافی است.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            handoffBlock?.let { block ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("بلوک تحویل آمادهٔ کپی", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "این متن را عیناً در چت عامل هوش مصنوعی خود بچسبانید. توکن فقط همین یک بار نمایش داده می‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        CodeBlock(block)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                clipboard.setText(AnnotatedString(block))
                                Toast.makeText(context, "بلوک تحویل کپی شد", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("کپی بلوک تحویل")
                            }
                            OutlinedButton(onClick = { handoffBlock = null }) { Text("بستن") }
                        }
                    }
                }
            }

            // تأیید عامل هوش مصنوعی — جفت‌سازی با رمز عامل‌ساخته (پل v0.7.1)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("تأیید عامل هوش مصنوعی", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "عامل شما خودش یک رمز ۵ رقمی ساخته و در چت نشان داده است؛ " +
                            "همان رمز را این‌جا وارد کنید تا پل بداند از طرف شما آمده است. " +
                            "هیچ رازی از چت عبور نمی‌کند — مهلت تأیید: ۲ دقیقه از ساخت رمز.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = pairCode,
                            onValueChange = { pairCode = it.filter(Char::isDigit).take(5) },
                            label = { Text("رمز ۵ رقمی عامل") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(170.dp),
                        )
                        Button(
                            onClick = { approvePairing() },
                            enabled = pairCode.length == 5 && !approving,
                        ) { Text("تأیید") }
                    }
                    pairingResult?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.KeyOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "منطقهٔ خطر",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        "باطل‌کردن کامل توکن عامل: عامل فوراً ۴۰۱ می‌گیرد. برای فعال‌کردن دوباره باید توکن جدید بسازید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    OutlinedButton(onClick = { showRevokeDialog = true }) {
                        Text("باطل کردن کامل دسترسی عامل", color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(onClick = { revokeAgentSessions() }) {
                        Text("باطل کردن نشست‌های موقت عامل‌ها", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showRotateDialog) {
        RotateDialog(
            onConfirm = { label ->
                showRotateDialog = false
                val api = session?.api ?: return@RotateDialog
                rotating = true
                error = null
                scope.launch {
                    try {
                        val token = withContext(Dispatchers.IO) { api.rotateAgentToken(label) }
                        SecretVault.saveAgentToken(context, serverId, token)
                        val block = withContext(Dispatchers.IO) { api.handoff() }
                        handoffBlock = block
                        refreshMeta()
                    } catch (e: Exception) {
                        error = "تولید توکن ناموفق: ${e.message}"
                    } finally {
                        rotating = false
                    }
                }
            },
            onDismiss = { showRotateDialog = false },
        )
    }

    if (showRevokeDialog) {
        ConfirmDialog(
            title = "باطل کردن دسترسی عامل؟",
            body = "عامل فعلی فوراً بی‌دسترسی می‌شود (401). اگر عامل وسط کاری باشد، دستور بعدی‌اش رد می‌شود.",
            confirmText = "باطل کن",
            danger = true,
            onConfirm = {
                showRevokeDialog = false
                val api = session?.api ?: return@ConfirmDialog
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { api.revokeAgentToken() }
                        refreshMeta()
                        Toast.makeText(context, "دسترسی عامل باطل شد", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
            onDismiss = { showRevokeDialog = false },
        )
    }

    if (rotating) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("در حال ساخت توکن و بلوک…") },
            text = { CircularProgressIndicator(Modifier.size(24.dp)) },
            confirmButton = { },
        )
    }
}

@Composable
private fun RotateDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("توکن عامل جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("برچسب (مثلاً «عامل توسعه — کلود»)") },
                    singleLine = true,
                )
                Text(
                    "توکن قبلی همین لحظه می‌میرد و بلوک تحویل تازه ساخته می‌شود. توکن در پاسخ فقط یک بار می‌آید و اپ آن را رمزنگاری‌شده نگه می‌دارد؛ هرگز در جای دیگری نشان داده نمی‌شود.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(label.trim()) }) { Text("بساز") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
