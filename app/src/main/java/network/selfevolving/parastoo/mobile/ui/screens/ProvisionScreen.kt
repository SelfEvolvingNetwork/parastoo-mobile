package network.selfevolving.parastoo.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.selfevolving.parastoo.mobile.core.data.Db
import network.selfevolving.parastoo.mobile.core.data.ServerProfileEntity
import network.selfevolving.parastoo.mobile.core.provision.ServerProvisioner
import network.selfevolving.parastoo.mobile.core.security.SecretVault

/**
 * جادوگر «افزودن سرور» — سناریوی مالک: کاربر فقط آدرس + رمز سرور تازه را
 * می‌دهد؛ اپ خودش پل (آجنت دسترسی) را با SSH نصب می‌کند، گواهی را TOFU ثبت
 * می‌کند، توکن ادمین را روی همین گوشی می‌سازد و سرور را به فهرست اضافه می‌کند.
 * رمز سرور در هیچ‌جا ذخیره نمی‌شود.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvisionScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var sshPort by remember { mutableStateOf("22") }
    var agentPort by remember { mutableStateOf("31563") }
    var password by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var resetTokens by remember { mutableStateOf(false) }

    var running by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("") }
    val logs = remember { mutableStateListOf<String>() }
    var logTick by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<ServerProvisioner.ProvisionResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    LaunchedEffect(logTick) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }
    // در میانهٔ نصب، برگشت معنا ندارد — نادیده گرفته می‌شود
    BackHandler(enabled = running) { }

    fun log(line: String) {
        logs.add(line)
        if (logs.size > 400) logs.removeRange(0, logs.size - 400)
        logTick++
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (running) "در حال نصب پل…" else "افزودن سرور") },
                navigationIcon = {
                    IconButton(onClick = onDone, enabled = !running) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                running -> {
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text(phase, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "این صفحه را نبندید؛ نصب معمولاً کمتر از یک دقیقه است.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            "لاگ زندهٔ سرور",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(logs.size) { i ->
                                Text(
                                    logs[i],
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(vertical = 1.dp),
                                )
                            }
                        }
                    }
                }

                result != null -> {
                    val res = result!!
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("✓ پل نصب و تأیید شد", style = MaterialTheme.typography.titleMedium)
                                Text("میزبان: ${res.hostname} — پل: v${res.agentVersion}")
                                Text(res.agentUrl, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "پین گواهی: ${res.certSha256.take(12)}…${res.certSha256.takeLast(6)}",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    "کلید میزبان SSH: ${res.sshHostKeyFp.take(12)}…${res.sshHostKeyFp.takeLast(6)}",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        Text(
                            "توکن ادمین روی همین گوشی ساخته شد و فقط در حافظهٔ رمزنگاری‌شده (Android Keystore) ماند. " +
                                "رمز سرور ذخیره نشد — برای نصب دوباره همان رمز را دوباره وارد می‌کنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    saving = true
                                    error = null
                                    try {
                                        val displayName = name.trim().ifBlank { "سرور ${res.hostname}" }
                                        val id = withContext(Dispatchers.IO) {
                                            Db.get().serverDao().insert(
                                                ServerProfileEntity(
                                                    name = displayName,
                                                    url = res.agentUrl,
                                                    certSha256 = res.certSha256,
                                                    createdAtMs = System.currentTimeMillis(),
                                                )
                                            )
                                        }
                                        withContext(Dispatchers.IO) {
                                            SecretVault.saveAdminToken(context, id, res.adminToken)
                                            SecretVault.saveAgentToken(context, id, res.agentToken)
                                        }
                                        password = ""
                                        onDone()
                                    } catch (e: Exception) {
                                        error = e.message ?: e.javaClass.simpleName
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (saving) "در حال ذخیره…" else "ذخیره و ادامه") }
                        error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                else -> {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "سرور را از شرکتِ تأمین‌کننده تحویل گرفتید؟ فقط آدرس و رمز را وارد کنید. " +
                                "اپ خودش پل را نصب می‌کند — لازم نیست وارد سرور شوید.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("نام دلخواه (اختیاری)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("آدرس سرور (IP یا دامنه)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("رمز سرور (root)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = advanced, onCheckedChange = { advanced = it })
                            Text("تنظیمات پیشرفته", style = MaterialTheme.typography.bodySmall)
                        }
                        if (advanced) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = sshPort,
                                    onValueChange = { sshPort = it.filter { c -> c.isDigit() }.take(5) },
                                    label = { Text("پورت SSH") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = agentPort,
                                    onValueChange = { agentPort = it.filter { c -> c.isDigit() }.take(5) },
                                    label = { Text("پورت پل") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = resetTokens, onCheckedChange = { resetTokens = it })
                                Column {
                                    Text("انتقال مالکیت پل به این گوشی", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "فقط وقتی سرور از قبل پل دارد؛ توکن‌های قبلی باطل می‌شوند.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Text(
                            "نکتهٔ امنیتی: رمز فقط داخل کانال رمزنگاری‌شدهٔ SSH برای نصب استفاده و " +
                                "بلافاصله فراموش می‌شود؛ توکن‌ها را خودِ گوشی می‌سازد و سرور فقط هش‌شان را نگه می‌دارد.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    running = true
                                    phase = "آماده‌سازی"
                                    error = null
                                    logs.clear()
                                    try {
                                        val res = withContext(Dispatchers.IO) {
                                            ServerProvisioner().provision(
                                                context = context,
                                                host = host.trim(),
                                                sshPort = sshPort.trim().toIntOrNull() ?: 22,
                                                password = password,
                                                agentPort = agentPort.trim().toIntOrNull() ?: 31563,
                                                resetTokens = resetTokens,
                                                onPhase = { p -> phase = p },
                                                onLog = { line -> log(line) },
                                            )
                                        }
                                        result = res
                                    } catch (e: Exception) {
                                        error = e.message ?: e.javaClass.simpleName
                                        log("خطا: $error")
                                    } finally {
                                        running = false
                                    }
                                }
                            },
                            enabled = host.isNotBlank() && password.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("نصب پل و اتصال") }
                        error?.let {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Text(
                                    it,
                                    Modifier.padding(12.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
