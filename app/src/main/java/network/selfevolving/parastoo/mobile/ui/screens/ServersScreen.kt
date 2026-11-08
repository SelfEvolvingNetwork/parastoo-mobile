package network.selfevolving.parastoo.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VpnKey
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.selfevolving.parastoo.mobile.R
import network.selfevolving.parastoo.mobile.core.data.Db
import network.selfevolving.parastoo.mobile.core.data.ServerProfileEntity
import network.selfevolving.parastoo.mobile.core.net.AccessAgentApi
import network.selfevolving.parastoo.mobile.core.security.PinnedTrust
import network.selfevolving.parastoo.mobile.core.security.SecretVault
import network.selfevolving.parastoo.mobile.ui.components.BannerKind
import network.selfevolving.parastoo.mobile.ui.components.ConfirmDialog
import network.selfevolving.parastoo.mobile.ui.components.EmptyState
import network.selfevolving.parastoo.mobile.ui.components.InfoBanner

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
    var confirmDelete by remember { mutableStateOf<ServerProfileEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_swallow),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("پرستو موبایل", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "سرورهای شما، همیشه همراه",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showAdd = true }) {
                        Text("افزودن دستی", style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddProvision,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("افزودن سرور") },
                shape = RoundedCornerShape(16.dp),
            )
        },
    ) { pad ->
        if (servers.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(horizontal = 16.dp),
                title = "خوش آمدید!",
                body = "سرور تازه تحویل گرفتید؟ فقط آدرس و رمز آن را وارد کنید.\n" +
                    "پرستو خودش وصل می‌شود، پل را نصب می‌کند و گواهی‌ها را ثبت می‌کند — " +
                    "شما هیچ‌وقت وارد سرور نمی‌شوید.",
                actionLabel = "افزودن سرور تازه",
                onAction = onAddProvision,
                secondaryLabel = "افزودن دستی با توکن آماده",
                onSecondary = { showAdd = true },
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            ) {
                items(servers, key = { it.id }) { s ->
                    ServerCard(
                        server = s,
                        onOpen = { onOpen(s.id) },
                        onDelete = { confirmDelete = s },
                    )
                }
            }
        }
    }

    confirmDelete?.let { s ->
        ConfirmDialog(
            title = "«${s.name}» حذف شود؟",
            body = "پروفایل و توکن‌های این سرور از همین گوشی پاک می‌شوند. خود سرور دست‌نخورده می‌ماند و بعداً می‌توانید دوباره اضافه‌اش کنید.",
            confirmText = "حذف کن",
            danger = true,
            onConfirm = {
                confirmDelete = null
                scope.launch(Dispatchers.IO) {
                    Db.get().serverDao().delete(s)
                    SecretVault.deleteAll(context, s.id)
                }
            },
            onDismiss = { confirmDelete = null },
        )
    }

    if (showAdd) {
        AddServerDialog(
            onDismiss = { showAdd = false },
            onSaved = { showAdd = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerCard(server: ServerProfileEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Dns,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(server.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    server.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Security,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "گواهی پین شد · ${server.certSha256.take(10)}…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "حذف سرور",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
        title = { Text("افزودن سرور اوبونتو", style = MaterialTheme.typography.titleLarge) },
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
                    leadingIcon = { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)) },
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
                    leadingIcon = { Icon(Icons.Rounded.Dns, null, Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = adminToken,
                    onValueChange = { adminToken = it },
                    label = { Text("توکن ادمین (ADMIN_TOKEN — فقط همین یک بار)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Rounded.VpnKey, null, Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    InfoBanner(
                        kind = if (ok) BannerKind.Success else BannerKind.Error,
                        text = msg,
                    )
                }
                fetchedFp?.let { fp ->
                    InfoBanner(
                        kind = BannerKind.Success,
                        title = "گواهی سرور دریافت و ثبت شد",
                        text = "$fp\nاز این پس هر تغییر گواهی سرور، اتصال متوقف می‌شود؛ اگر روزی گواهی را عوض کردید، سرور را حذف و دوباره اضافه کنید.",
                    )
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
