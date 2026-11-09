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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.selfevolving.parastoo.mobile.core.config.CoreConfigStore
import network.selfevolving.parastoo.mobile.core.data.Db
import network.selfevolving.parastoo.mobile.core.model.HostStatus
import network.selfevolving.parastoo.mobile.core.model.SetupTokenInfo
import network.selfevolving.parastoo.mobile.core.net.AccessAgentApi
import network.selfevolving.parastoo.mobile.core.security.SecretVault
import network.selfevolving.parastoo.mobile.ui.components.BackTopBar
import network.selfevolving.parastoo.mobile.ui.components.ConfirmDialog
import network.selfevolving.parastoo.mobile.ui.components.SectionLabel
import network.selfevolving.parastoo.mobile.ui.components.ServerSession
import network.selfevolving.parastoo.mobile.ui.components.rememberSession

/**
 * تنظیمات سرور — بازنگری چیدمان (فرمان مالک 2026-11-09): «هر کار، خانهٔ خودش».
 * داشبورد فقط وضعیت و ناوبری است؛ هر چیزِ قابل‌تنظیم/قابل‌نگهداری/خطرناکِ
 * همین سرور این‌جاست و در سه بخش مرتب می‌شود:
 *   ۱) دسترسی و کلیدها — قلمرو عامل (کلید توقف)، توکن گیت‌هاب، توکن نصب سایت
 *   ۲) نگهداری — به‌روزرسانی خودکار پل
 *   ۳) منطقهٔ خطر — حذف خود پل، حذف این سرور از اپ
 * هیچ منطق جدیدی این‌جا ساخته نمی‌شود؛ کارت‌ها عیناً از داشبورد پیشین منتقلند
 * تا رفتار تأییده‌های دولایه دقیقاً همان بماند.
 */
@Composable
fun ServerSettingsScreen(
    serverId: Long,
    onBack: () -> Unit,
    onReturnToList: () -> Unit,
) {
    val session = rememberSession(serverId)
    var pauseDialog by remember { mutableStateOf<Boolean?>(null) } // null بسته؛ true/false هدف
    var ghDialog by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<HostStatus?>(null) }
    var statusError by remember { mutableStateOf<String?>(null) }
    var setupInfo by remember { mutableStateOf<SetupTokenInfo?>(null) }
    var setupErr by remember { mutableStateOf<String?>(null) }
    var setupLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    /** توکن نصب سایت — هر بار تازه خوانده می‌شود؛ هیچ‌چیز روی گوشی نمی‌ماند */
    fun loadSetupToken(api: AccessAgentApi) {
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

    /** وضعیت سرور فقط برای پیکان کلید توقف و توکن گیت‌هاب؛ خطا در تنظیمات بحرانی نیست */
    fun refreshStatus(api: AccessAgentApi) {
        scope.launch {
            try {
                status = withContext(Dispatchers.IO) { api.status() }
                statusError = null
            } catch (e: Exception) {
                if (status == null) statusError = e.message ?: e.javaClass.simpleName
            }
            loadSetupToken(api)
        }
    }

    LaunchedEffect(session) {
        val api = session?.api ?: return@LaunchedEffect
        refreshStatus(api)
    }
    Column(Modifier.fillMaxSize()) {
        BackTopBar(
            title = "تنظیمات سرور",
            subtitle = session?.profile?.name,
            onBack = onBack,
        )
        val s = session
        if (s == null) {
            Text(
                "این سرور روی گوشی پیدا نشد.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        } else {
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
                SectionLabel("دسترسی و کلیدها")

                // کلید توقف قلمرو عامل — از داشبورد به خانهٔ خودش منتقل شد
                AgentRealmCard(
                    st = status,
                    loading = status == null && statusError == null,
                    onToggle = { pauseDialog = it },
                )

                // توکن گیت‌هاب برای عامل — فرمان مالک (2026-11-09)
                GithubTokenCard(
                    stored = status?.githubTokenStored,
                    onEdit = { ghDialog = true },
                )

                // توکن نصب سایت — فرمان مالک (2026-11-09): کاربر برای دیدن
                // توکن نصب نباید به سرور مراجعه کند؛ از کانال پین‌شده می‌آید
                SetupTokenCard(
                    info = setupInfo,
                    loading = setupLoading,
                    error = setupErr,
                    onRetry = { s.api.let { loadSetupToken(it) } },
                )

                SectionLabel("نگهداری")

                // به‌روزرسانی خودکار پل — فرمان مالک (2026-11-09): بدون پرسش
                BridgeAutoUpdateCard(
                    session = s,
                    currentVersion = status?.version ?: "",
                    onRefresh = { refreshStatus(s.api) },
                )

                SectionLabel("منطقهٔ خطر")

                // آخرین فرمان نقطهٔ صفر (فرمان مالک 2026-11-09: «پرستو موبایل
                // دستور پاک کردن پل را می‌دهد»). فلگ هات فقط سطح را کم کند:
                // پنهان‌کردن بله، هرگز نمایشِ چیزی کمترِ خطر نه.
                if (CoreConfigStore.get().flag("bridge_self_destruct_visible", true)) {
                    BridgeSelfDestructCard(session = s, onBack = onReturnToList)
                }
                RemoveServerCard(session = s, onRemoved = onReturnToList)

                Text(
                    "تأییدهای تخریبی دولایه‌اند و هرگز از کانال هات قابل نرم‌شدن نیستند.",
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
                        api.let { refreshStatus(it) }
                    } catch (e: Exception) {
                        statusError = e.message
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
                onSaved = { refreshStatus(sNow.api) },
                onDismiss = { ghDialog = false },
            )
        }
    }
}

/** کارت قلمرو عامل + کلید توقف — منتقل‌شده از داشبورد، عیناً */
@Composable
private fun AgentRealmCard(
    st: HostStatus?,
    loading: Boolean,
    onToggle: (Boolean) -> Unit,
) {
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
                        loading -> "در حال خواندن وضعیت…"
                        st == null -> "وضعیت خوانده نشد"
                        !st.agentRealmEnabled -> "غیرفعال (توکن عامل وجود ندارد)"
                        st.agentRealmPaused -> "موقفاً متوقف (کلید توقف)"
                        else -> "فعال و مجاز به اجرای دستور"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        st != null && st.agentRealmPaused -> MaterialTheme.colorScheme.tertiary
                        st != null && st.agentRealmEnabled -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            when {
                loading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                st != null -> Switch(
                    checked = st.agentRealmPaused,
                    onCheckedChange = onToggle,
                )
            }
        }
    }
}

/** کارت توکن گیت‌هاب — منتقل‌شده از داشبورد، عیناً */
@Composable
private fun GithubTokenCard(stored: Boolean?, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Key,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (stored == true) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("توکن گیت‌هاب برای عامل", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    when (stored) {
                        true -> "ذخیره شده — عامل توکن موقتِ هدفمند می‌گیرد"
                        false -> "تنظیم نشده — عامل به گیت‌هاب دسترسی ندارد"
                        null -> "در حال خواندن وضعیت…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (stored == true) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onEdit, enabled = stored != null) {
                Text(if (stored == true) "تعویض" else "تنظیم")
            }
        }
    }
}

/** دیالوگ توکن گیت‌هاب — فرمان مالک (2026-11-09): اختیاری، از مالک پروژه، یک‌جای مناسب سرور */
@Composable
private fun GithubTokenDialog(
    replacing: Boolean,
    api: AccessAgentApi,
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

/**
 * کارت «حذف این سرور از اپ» — ملایم‌ترین گزینهٔ منطقهٔ خطر: فقط پروفایل و
 * رازهای همین گوشی پاک می‌شود؛ خود سرور و پل دست‌نخورده می‌مانند و با
 * «افزودن سرور» هر وقت دوباره برمی‌گردند.
 */
@Composable
private fun RemoveServerCard(session: ServerSession, onRemoved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("حذف این سرور از اپ", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "فقط پروفایل و توکن‌های روی این گوشی پاک می‌شوند؛ خود سرور و پل دست‌نخورده می‌مانند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { confirm = true }) {
                Text("حذف از اپ…", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = "«${session.profile.name}» از اپ حذف شود؟",
            body = "پروفایل و توکن‌های این سرور از همین گوشی پاک می‌شوند. خود سرور دست‌نخورده می‌ماند و بعداً می‌توانید دوباره اضافه‌اش کنید.",
            confirmText = "حذف کن",
            danger = true,
            onConfirm = {
                confirm = false
                scope.launch {
                    withContext(Dispatchers.IO) {
                        Db.get().serverDao().delete(session.profile)
                        SecretVault.deleteAll(context, session.profile.id)
                    }
                    onRemoved()
                }
            },
            onDismiss = { confirm = false },
        )
    }
}
