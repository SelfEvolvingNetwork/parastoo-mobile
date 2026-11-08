package network.selfevolving.parastoo.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.selfevolving.parastoo.mobile.core.data.Db
import network.selfevolving.parastoo.mobile.core.data.ServerProfileEntity
import network.selfevolving.parastoo.mobile.core.net.AccessAgentApi
import network.selfevolving.parastoo.mobile.core.security.PinnedTrust
import network.selfevolving.parastoo.mobile.core.security.SecretVault

/**
 * فهرست سرورها + نقطهٔ ورود «افزودن سرور»: مسیر اصلی جادوگر فراهم‌سازی است
 * (فقط آدرس + رمز — اپ خودش پل را نصب می‌کند؛ سناریوی مالک 2026-11-08).
 * مسیر دستی (آدرس + توکن، برای پلی که از قبل نصب است) در action نوار بالا است.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(onOpen: (Long) -> Unit, onAddProvision: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val servers by Db.get().serverDao().observeAll().collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("پرستو موبایل") },
                actions = {
                    TextButton(onClick = { showAdd = true }) {
                        Text("افزودن دستی")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddProvision,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("افزودن سرور") },
            )
        },
    ) { pad ->
        if (servers.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(pad).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "هنوز سروری اضافه نکرده‌اید",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "فقط آدرس سرور و رمز آن را وارد کنید؛\nاپ خودش پل را روی سرور تازه نصب می‌کند و\nگواهی را ثبت (TOFU) می‌کند. ورود بعدی فقط\nبا قفل گوشی است.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onAddProvision) { Text("شروع نصب") }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(servers, key = { it.id }) { s ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(s.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    s.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    "پین: ${s.certSha256.take(12)}…${s.certSha256.takeLast(6)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            TextButton(onClick = { onOpen(s.id) }) { Text("مدیریت") }
                            IconButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    Db.get().serverDao().delete(s)
                                    SecretVault.deleteAll(context, s.id)
                                }
                            }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "حذف سرور",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddServerDialog(
            onDismiss = { showAdd = false },
            onSaved = { showAdd = false },
        )
    }
}

@Composable
private fun AddServerDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var adminToken by remember { mutableStateOf("") }
    // اثرانگشتی که اپ در اولین اتصال خودش گرفته (TOFU)؛ با تغییر آدرس باطل می‌شود
    var fetchedFp by remember { mutableStateOf<String?>(null) }
    var testState by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var testing by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val canSave = name.isNotBlank() && url.isNotBlank() && adminToken.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن سرور اوبونتو") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام دلخواه (مثلاً سرور مُحسن)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        // اثرانگشت به آدرس گره خورده است؛ تغییر آدرس یعنی واکشی دوباره
                        if (it != url) {
                            fetchedFp = null
                            testState = null
                        }
                        url = it
                    },
                    label = { Text("آدرس (مثل 193.151.142.128:31563)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = adminToken,
                    onValueChange = { adminToken = it },
                    label = { Text("توکن ادمین (ADMIN_TOKEN — فقط همین یک بار)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = url.isNotBlank() && adminToken.isNotBlank() && !testing,
                        onClick = {
                            testing = true
                            testState = null
                            scope.launch {
                                testState = try {
                                    val u = AccessAgentApi.normalizeUrl(url)
                                    // TOFU: اولین اتصال، خودش اثرانگشت گواهی را می‌گیرد
                                    val fp = withContext(Dispatchers.IO) { PinnedTrust.fetchFingerprint(u) }
                                    fetchedFp = PinnedTrust.normalizeFingerprint(fp)
                                    val api = AccessAgentApi(u, fp, adminToken)
                                    val st = withContext(Dispatchers.IO) { api.status() }
                                    true to "اتصال موفق — میزبان: ${st.hostname}، پل: v${st.version}"
                                } catch (e: Exception) {
                                    false to "اتصال ناموفق: ${e.message ?: e.javaClass.simpleName}"
                                }
                                testing = false
                            }
                        },
                    ) { Text(if (testing) "در حال تست…" else "تست اتصال") }
                    if (testing) CircularProgressIndicator(Modifier.size(20.dp))
                }
                testState?.let { (ok, msg) ->
                    Text(
                        msg,
                        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                fetchedFp?.let { fp ->
                    Column {
                        Text(
                            "گواهی سرور دریافت و ثبت شد (اعتماد در اولین اتصال، مثل SSH):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            fp,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            "از این پس هر تغییر گواهی سرور، اتصال متوقف می‌شود؛ اگر روزی گواهی را عوض کردید، سرور را حذف و دوباره اضافه کنید.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "توکن ادمین فقط در حافظهٔ رمزنگاری‌شدهٔ خود گوشی (Android Keystore) ذخیره می‌شود و هرگز جایی ارسال/نمایش نمی‌شود.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                err?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave && !testing,
                onClick = {
                    scope.launch {
                        try {
                            val u = AccessAgentApi.normalizeUrl(url)
                            // اگر ادمین بدون تست ذخیره کند، خودِ ذخیره اثرانگشت را می‌گیرد
                            val fp = withContext(Dispatchers.IO) {
                                fetchedFp ?: PinnedTrust.fetchFingerprint(u)
                            }
                            fetchedFp = PinnedTrust.normalizeFingerprint(fp)
                            val id = withContext(Dispatchers.IO) {
                                Db.get().serverDao().insert(
                                    ServerProfileEntity(
                                        name = name.trim(),
                                        url = u,
                                        certSha256 = PinnedTrust.normalizeFingerprint(fp),
                                        createdAtMs = System.currentTimeMillis(),
                                    )
                                )
                            }
                            SecretVault.saveAdminToken(context, id, adminToken.trim())
                            onSaved()
                        } catch (e: Exception) {
                            err = e.message ?: "ذخیره ناموفق بود"
                        }
                    }
                },
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
