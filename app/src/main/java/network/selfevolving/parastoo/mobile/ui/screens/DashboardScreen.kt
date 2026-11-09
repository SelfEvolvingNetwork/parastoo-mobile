package network.selfevolving.parastoo.mobile.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.selfevolving.parastoo.mobile.core.config.CoreConfigStore
import network.selfevolving.parastoo.mobile.core.model.HostStatus
import network.selfevolving.parastoo.mobile.core.model.SetupTokenInfo
import network.selfevolving.parastoo.mobile.ui.components.ActionTile
import network.selfevolving.parastoo.mobile.ui.components.BackTopBar
import network.selfevolving.parastoo.mobile.ui.components.EmptyState
import network.selfevolving.parastoo.mobile.ui.components.ServerSession
import network.selfevolving.parastoo.mobile.ui.components.StatCard
import network.selfevolving.parastoo.mobile.ui.components.StatusDot
import network.selfevolving.parastoo.mobile.ui.components.rememberSession

/** داشبورد سرور — وضعیت زندهٔ هاست + وضعیت قلمرو عامل + کلید توقف + مدیریت پرستو */
@Composable
fun DashboardScreen(
    serverId: Long,
    onMonitor: () -> Unit,
    onAgent: () -> Unit,
    onLifecycle: () -> Unit,
    onBack: () -> Unit,
) {
    val session = rememberSession(serverId)
    var status by remember { mutableStateOf<HostStatus?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var pauseDialog by remember { mutableStateOf<Boolean?>(null) } // null بسته؛ true/false هدف
    var ghDialog by remember { mutableStateOf(false) }
    var setupInfo by remember { mutableStateOf<SetupTokenInfo?>(null) }
    var setupErr by remember { mutableStateOf<String?>(null) }
    var setupLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // هستهٔ هات — واکشی بی‌سروصدا از همین پل (v0.4.12)؛ هر خطا = ادامه با
    // نسخهٔ فعلی؛ فلگ/متن با تغییرش صفحه خودکار تازه می‌شود
    LaunchedEffect(session?.profile?.id) {
        val s = session ?: return@LaunchedEffect
        CoreConfigStore.refresh { s.api.coreConfig() }
    }

    /** توکن نصب سایت — هر بار تازه خوانده می‌شود؛ هیچ‌چیز روی گوشی نمی‌ماند */
    fun loadSetupToken(api: network.selfevolving.parastoo.mobile.core.net.AccessAgentApi) {
        scope.launch {
            setupLoading = true
            try {
                setupInfo = withContext(Dispatchers.IO) { api.setupToken() }
                setupErr = null
            } catch (e: Exception) {
                if (setupInfo == null) setupErr = e.message ?: e.javaClass.simpleName
            } finally {
                setupLoading = false
            }
        }
    }

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
            loadSetupToken(api)
        }
    }

    LaunchedEffect(session) {
        val api = session?.api ?: return@LaunchedEffect
        loadSetupToken(api)
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

    Column(Modifier.fillMaxSize()) {
        BackTopBar(
            title = session?.profile?.name ?: "سرور",
            subtitle = session?.profile?.url,
            onBack = onBack,
        )
        val s = session
        if (s == null) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                EmptyState(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    icon = Icons.Rounded.Dns,
                    title = "این سرور آماده نیست",
                    body = "توکن ادمین روی این گوشی پیدا نشد؛ سرور را از فهرست حذف و دوباره اضافه کنید.",
                )
            }
        } else {
            val st = status
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
                if (st == null) {
                    // هنوز وضعیتی نرسیده: بارگذاری یا خطا
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (loading) {
                                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                                Spacer(Modifier.height(10.dp))
                            } else {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                            Text(
                                if (error != null) "به سرور وصل نمی‌شود" else "در حال گرفتن وضعیت…",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            error?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (error != null) {
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = { refresh(s.api) }) {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        null,
                                        Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("تلاش دوباره")
                                }
                            }
                        }
                    }
                } else {
                    HeroStatusCard(st)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(
                            "دیسک آزاد",
                            st.diskFreeGb?.let { "%.1f گیگابایت".format(it) } ?: "—",
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Storage,
                        )
                        StatCard(
                            "رم آزاد",
                            st.memAvailMb?.let { "${it / 1024} از ${st.memTotalMb?.div(1024)} گیگ" } ?: "—",
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Memory,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(
                            "آپ‌تایم سرور",
                            formatUptime(st.uptimeS),
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Schedule,
                        )
                        StatCard(
                            "پرستو",
                            if (st.parastooInstallPath.isEmpty()) "نصب نشده" else "نصب است",
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Cloud,
                        )
                    }

                    // قلمرو عامل + کلید توقف
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("قلمرو عامل هوش مصنوعی", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    when {
                                        !st.agentRealmEnabled -> "غیرفعال (توکن عامل وجود ندارد)"
                                        st.agentRealmPaused -> "موقفاً متوقف (کلید توقف)"
                                        else -> "فعال و مجاز به اجرای دستور"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        st.agentRealmPaused -> MaterialTheme.colorScheme.tertiary
                                        st.agentRealmEnabled -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            Switch(
                                checked = st.agentRealmPaused,
                                onCheckedChange = { pauseDialog = it },
                            )
                        }
                    }

                    // توکن گیت‌هاب برای عامل — فرمان مالک (2026-11-09)
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 6.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Key,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (st.githubTokenStored) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("توکن گیت‌هاب برای عامل", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (st.githubTokenStored) "ذخیره شده — عامل توکن موقتِ هدفمند می‌گیرد"
                                    else "تنظیم نشده — عامل به گیت‌هاب دسترسی ندارد",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (st.githubTokenStored) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { ghDialog = true }) {
                                Text(if (st.githubTokenStored) "تعویض" else "تنظیم")
                            }
                        }
                    }

                    // توکن نصب سایت — فرمان مالک (2026-11-09): کاربر برای دیدن
                    // توکن نصب نباید به سرور مراجعه کند؛ از کانال پین‌شده می‌آید
                    SetupTokenCard(
                        info = setupInfo,
                        loading = setupLoading,
                        error = setupErr,
                        onRetry = { s.api.let { loadSetupToken(it) } },
                    )

                    ActionTile(
                        icon = Icons.Rounded.Monitor,
                        title = "مانیتور زنده",
                        subtitle = "هر رویداد پل، همان لحظه روی گوشی",
                        onClick = onMonitor,
                    )
                    ActionTile(
                        icon = Icons.Rounded.VpnKey,
                        title = "ایجنت و بلوک تحویل",
                        subtitle = "ساخت توکن عامل جدید، ابطال دسترسی",
                        onClick = onAgent,
                    )
                    ActionTile(
                        icon = Icons.Rounded.Settings,
                        title = "مدیریت پرستو",
                        subtitle = "نصب، حذف کامل، نصب مجدد و به‌روزرسانی — بدون ورود به سرور",
                        onClick = onLifecycle,
                    )
                    // به‌روزرسانی خودکار پل — فرمان مالک (2026-11-09): بدون پرسش
                    BridgeAutoUpdateCard(
                        session = s,
                        currentVersion = st.version,
                        onRefresh = { refresh(s.api) },
                    )
                    // به‌روزرسانی خودکار خود اپ — حلقهٔ آخر آپدیت‌ها (فرمان مالک)
                    AppAutoUpdateCard()

                    // منطقهٔ خطر — آخرین فرمان نقطهٔ صفر (فرمان مالک 2026-11-09:
                    // «پرستو موبایل دستور پاک کردن پل را می‌دهد»). فلگ هات فقط
                    // می‌تواند سطح را کم کند: پنهان‌کردن بله، هرگز نمایشِ چیزی
                    // کمترِ خطر نه.
                    if (CoreConfigStore.get().flag("bridge_self_destruct_visible", true)) {
                        BridgeSelfDestructCard(session = s, onBack = onBack)
                    }

                    Text(
                        "وضعیت هر ۱۵ ثانیه خودکار تازه می‌شود.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                        api.let { refresh(it) }
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
            onDismiss = { pauseDialog = null },
        )
    }

    if (ghDialog) {
        val sNow = session
        if (sNow != null) {
            GithubTokenDialog(
                replacing = status?.githubTokenStored == true,
                api = sNow.api,
                onSaved = { refresh(sNow.api) },
                onDismiss = { ghDialog = false },
            )
        }
    }
}

/** کارت قهرمان وضعیت: در یک نگاه معلوم است همه‌چیز مرتب است یا نه */
@Composable
private fun HeroStatusCard(s: HostStatus) {
    val healthy = s.agentRealmEnabled && !s.agentRealmPaused
    val container = when {
        s.agentRealmPaused -> MaterialTheme.colorScheme.tertiaryContainer
        healthy -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onContainer = when {
        s.agentRealmPaused -> MaterialTheme.colorScheme.onTertiaryContainer
        healthy -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        s.agentRealmPaused -> "عامل موقتاً متوقف است"
                        healthy -> "همه‌چیز سلامت است"
                        else -> "قلمرو عامل بسته است"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainer,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "${s.hostname} · پل v${s.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            StatusDot(ok = healthy)
        }
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

/** دیالوگ توکن گیت‌هاب — فرمان مالک (2026-11-09): اختیاری، از مالک پروژه، یک‌جای مناسب سرور */
@Composable
private fun GithubTokenDialog(
    replacing: Boolean,
    api: network.selfevolving.parastoo.mobile.core.net.AccessAgentApi,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (replacing) "تعویض توکن گیت‌هاب" else "تنظیم توکن گیت‌هاب") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "این توکن را مالک پروژه از گیت‌هاب می‌گیرد (توصیه: Fine-grained، فقط مخازن پروستو، Contents: Read and Write، انقضای کوتاه). توکن فقط یک بار برای همین سرور شما فرستاده می‌شود؛ پل آن را با دسترسی ۶۰۰ نگه می‌دارد و به عامل هوش مصنوعی فقط «توکن موقتِ هدفمند» می‌دهد. روی این گوشی ذخیره نمی‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.trim() },
                    label = { Text("توکن گیت‌هاب") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                err?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && token.length >= 20,
                onClick = {
                    scope.launch {
                        busy = true
                        err = null
                        try {
                            withContext(Dispatchers.IO) { api.setGithubToken(token) }
                            onSaved()
                            onDismiss()
                        } catch (e: Exception) {
                            err = "ذخیره نشد: ${e.message} — چند لحظه بعد دوباره تلاش کنید."
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(if (busy) "در حال ذخیره…" else "ذخیره روی سرور") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("انصراف") }
        },
    )
}

/**
 * کارت «توکن نصب سایت» — فرمان مالک (2026-11-09): کاربر برای دیدن توکن نصب
 * نباید به سرور مراجعه کند. توکن از کانال TLS پین‌شدهٔ خود کاربر می‌آید،
 * به‌صورت ماسک‌شده نمایش داده می‌شود، با یک لمس کپی و با یک لمس جادوگر پنل
 * باز می‌شود. قرارداد راز: روی گوشی ذخیرهٔ پایدار نمی‌شود (فقط حافظهٔ فراخوانی).
 */
@Composable
private fun SetupTokenCard(
    info: SetupTokenInfo?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Password,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (info?.hasToken == true) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("توکن نصب سایت", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when {
                            info == null && loading -> "در حال خواندن از سرور…"
                            info == null && error != null -> "خوانده نشد"
                            info?.installed == false -> "پرستو هنوز نصب نشده"
                            info?.hasToken == true -> "برای پایان جادوگر پنل — بدون ورود به سرور"
                            else -> "در سرور ثبت نشده"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (info?.hasToken == true) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                info == null && error != null -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تلاش دوباره")
                    }
                }
                info != null && info.hasToken -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (revealed) info.setupToken else maskSetupToken(info.setupToken),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row {
                        TextButton(onClick = { revealed = !revealed }) {
                            Text(if (revealed) "پنهان کن" else "نمایش")
                        }
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(info.setupToken))
                            copied = true
                            scope.launch {
                                delay(2500)
                                copied = false
                            }
                        }) { Text(if (copied) "کپی شد" else "کپی") }
                        if (info.setupUrl.isNotEmpty()) {
                            TextButton(onClick = { uriHandler.openUri(info.setupUrl) }) {
                                Text("رفتن به جادوگر")
                            }
                        }
                    }
                    Text(
                        "این توکن فقط از کانال امن خودتان می‌آید و روی گوشی ذخیره نمی‌شود؛ به کسی نشانش ندهید.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                info != null && !info.installed -> {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "پس از نصب پرستو از «مدیریت پرستو»، توکن همین‌جا نمایش داده می‌شود.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** ماسک امن نمایشی: الگوی آشنا cccc-••••-•••• حفظ می‌شود تا قابل تشخیص باشد */
private fun maskSetupToken(token: String): String {
    val parts = token.split("-")
    return when {
        parts.size >= 3 -> parts[0] + "-••••-••••"
        token.length > 4 -> token.take(2) + "••••" + token.takeLast(2)
        else -> "••••"
    }
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

/**
 * کارت «حذف خود پل» — آخرین فرمان نقطهٔ صفر (پل v0.5.7). پل همهٔ چیزِ خودش را
 * پاک می‌کند و بعد از آن هیچ دسترسی راه‌دوری برای هیچ‌کس باقی نمی‌ماند —
 * نه اپ، نه عامل، نه پنل. تأیید دولایه: دیالوگ هشدار + تایپ عبارت.
 * پاسخ‌نگرفتن پس از فرمان، «مسیر موفقیت» است — اپ خودش همین را می‌گوید.
 */
@Composable
private fun BridgeSelfDestructCard(
    session: ServerSession,
    onBack: () -> Unit,
) {
    // متن‌های هات (فقط allowlist توصیفی؛ عنوان/برچسب/کلمهٔ تأیید همیشه
    // کامپایل‌شده می‌مانند — قرارداد §۵.۸ پروتکل پل)
    val cfg = CoreConfigStore.get()
    var pendingConfirm by remember { mutableStateOf(false) }
    var scheduled by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        ),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("حذف خود پل (آخرین فرمان)", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        cfg.str(
                            "bridge_uninstall_card_subtitle",
                            "پس از حذف کامل پرستو — پل هم خودش را پاک می‌کند و هیچ دسترسی راه‌دوری باقی نمی‌ماند",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                scheduled -> {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "فرمان حذف پل صادر شد — نقطهٔ صفر مطلق",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        cfg.str(
                            "bridge_uninstall_scheduled_body",
                            "پل دارد خودش و همهٔ راز‌هایش را پاک می‌کند؛ از این لحظه پاسخ نگرفتن این سرور «عادی» است — " +
                                "هیچ توکنی دیگر معتبر نیست و هیچ‌کس از راه دور وارد نمی‌شود. " +
                                "برای برگشتن، از «افزودن سرور» با رمز root شروع کنید — مثل اولین روز.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onBack) { Text("بازگشت به فهرست سرورها") }
                }
                else -> {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { pendingConfirm = true }) {
                        Text("حذف پل…", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            err?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (pendingConfirm && !scheduled) {
        BridgeTypedConfirmDialog(
            title = "حذف خود پل",
            body = cfg.str(
                "bridge_uninstall_confirm_body",
                "این آخرین فرمان است و بازگشتی ندارد: پل کد، توکن‌های ادمین و عامل، گواهی، گاوصندوق گیت‌هاب و " +
                    "گزارش رویدادهای خودش را برای همیشه پاک می‌کند. بعد از آن هیچ‌کس — حتی این اپ — از راه دور وارد سرور " +
                    "نمی‌شود و اتصال وسط کار قطع می‌شود (این خودش نشانهٔ موفقیت است). تنها راه بازگشت: جریان «افزودن سرور" +
                    "» با رمز root. برای تأیید بنویسید: حذف پل",
            ),
            word = "حذف پل",
            busy = busy,
            onConfirm = {
                val api = session.api
                scope.launch {
                    busy = true
                    err = null
                    try {
                        withContext(Dispatchers.IO) { api.bridgeUninstall() }
                        // 202 یعنی واحد گذرای systemd زمان‌بندی شد؛ از این‌جا به بعد
                        // هر تلاش اتصال قرار است شکست بخورد — این موفقیت است.
                        scheduled = true
                    } catch (e: Exception) {
                        err = "فرمان صادر نشد: ${e.message ?: e.javaClass.simpleName}"
                    } finally {
                        busy = false
                    }
                }
            },
            onDismiss = { if (!busy) pendingConfirm = false },
        )
    }
}

/** دیالوگ تأیید با تایپ عبارت — همان الگوی «حذف کامل پرستو» */
@Composable
private fun BridgeTypedConfirmDialog(
    title: String,
    body: String,
    word: String,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(body, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("عبارت تأیید") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            val ok = typed.trim() == word && !busy
            TextButton(enabled = ok, onClick = onConfirm) {
                Text(
                    if (busy) "در حال صدور فرمان…" else "انجام بده",
                    color = if (ok) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("انصراف") } },
    )
}
