package network.selfevolving.parastoo.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.selfevolving.parastoo.mobile.core.data.Db
import network.selfevolving.parastoo.mobile.core.data.ServerProfileEntity
import network.selfevolving.parastoo.mobile.core.provision.ServerProvisioner
import network.selfevolving.parastoo.mobile.core.security.SecretVault
import network.selfevolving.parastoo.mobile.ui.components.BannerKind
import network.selfevolving.parastoo.mobile.ui.components.DetailRow
import network.selfevolving.parastoo.mobile.ui.components.InfoBanner
import network.selfevolving.parastoo.mobile.ui.components.PrimaryCta
import network.selfevolving.parastoo.mobile.ui.components.SectionLabel
import network.selfevolving.parastoo.mobile.ui.theme.TerminalBg
import network.selfevolving.parastoo.mobile.ui.theme.TerminalError
import network.selfevolving.parastoo.mobile.ui.theme.TerminalText

/**
 * جادوگر «افزودن سرور» — سناریوی مالک: کاربر فقط آدرس + رمز سرور تازه را
 * می‌دهد؛ اپ خودش پل (آجنت دسترسی) را با SSH نصب می‌کند، گواهی را TOFU ثبت
 * می‌کند، توکن ادمین را روی همین گوشی می‌سازد و سرور را به فهرست اضافه می‌کند.
 * رمز سرور در هیچ‌جا ذخیره نمی‌شود.
 */

/** مراحل نصب — متن فازهایی که ServerProvisioner گزارش می‌کند به همین‌ها نگاشت می‌شود */
private val PROVISION_STEPS = listOf(
    Icons.Rounded.CloudUpload to "بررسی بستهٔ نصب",
    Icons.Rounded.VpnKey to "اتصال امن SSH",
    Icons.Rounded.Lock to "ورود با رمز سرور",
    Icons.Rounded.Upload to "انتقال بسته به سرور",
    Icons.Rounded.Terminal to "نصب پل روی سرور",
    Icons.Rounded.Security to "ثبت گواهی و کلیدها",
    Icons.Rounded.CheckCircle to "تست نهایی اتصال",
    Icons.Rounded.Key to "آماده‌سازی توکن هوش مصنوعی",
)

private fun stepIndexFor(phase: String): Int = when {
    // «بالا بردن بستهٔ نصب» هم «بسته» دارد — ترتیب بررسی مهم است
    phase.contains("بالا بردن") -> 3
    phase.contains("احراز") -> 2
    phase.contains("اجرای") -> 4
    phase.contains("گواهی") -> 5
    phase.contains("نهایی") -> 6
    phase.contains("توکن") -> 7
    phase.contains("انجام شد") -> 8
    phase.contains("اتصال") -> 1
    phase.contains("بسته") -> 0
    else -> 0
}

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
    var showPassword by remember { mutableStateOf(false) }
    var githubToken by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }

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
                title = { Text(if (running) "در حال نصب پل…" else "افزودن سرور", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onDone, enabled = !running) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val view = when {
                running -> "running"
                result != null -> "done"
                else -> "form"
            }
            Crossfade(targetState = view, label = "provisionState") { v ->
                when (v) {
                    "running" -> RunningView(phase, logs, listState)

                    "done" -> {
                        val res = result!!
                        DoneView(
                            res = res,
                            saving = saving,
                            error = error,
                            onSave = {
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
                                            // فرمان مالک (2026-11-09): رمز یک بار گرفته می‌شود و تمام —
                                            // پس از نصب موفق در گاوصندوق قفل می‌شود تا به‌روزرسانی خودکار
                                            // پل و کارهای بعدی هرگز دوباره نپرسند.
                                            SecretVault.saveSshPassword(context, id, password)
                                            SecretVault.saveSshPort(context, id, sshPort.trim().toIntOrNull() ?: 22)
                                            if (res.sshHostKeyFp.isNotBlank()) {
                                                SecretVault.saveSshHostKeyFp(context, id, res.sshHostKeyFp)
                                            }
                                        }
                                        password = ""
                                        githubToken = ""
                                        onDone()
                                    } catch (e: Exception) {
                                        error = e.message ?: e.javaClass.simpleName
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                        )
                    }

                    else -> FormView(
                        name = name,
                        host = host,
                        sshPort = sshPort,
                        agentPort = agentPort,
                        password = password,
                        showPassword = showPassword,
                        githubToken = githubToken,
                        advanced = advanced,
                        error = error,
                        onName = { name = it },
                        onHost = { host = it },
                        onSshPort = { sshPort = it },
                        onAgentPort = { agentPort = it },
                        onPassword = { password = it },
                        onGithubToken = { githubToken = it },
                        onTogglePassword = { showPassword = !showPassword },
                        onAdvanced = { advanced = it },
                        onInstall = {
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
                                            githubToken = githubToken.trim(),
                                            onPhase = { p -> phase = p },
                                            onLog = { line -> log(line) },
                                        )
                                    }
                                    result = res
                                } catch (e: Exception) {
                                    error = friendlyError(e)
                                    log("خطا: $error")
                                } finally {
                                    running = false
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

// ---------- حالت فرم: فقط آدرس + رمز ----------

// ---------- نگاشت خطا به زبان آدمیزاد — کاربر نباید متن استثنای فنی ببیند ----------

private fun friendlyError(e: Throwable): String {
    val msg = (e.message ?: "").lowercase()
    return when {
        // خطاهای خودمان که از قبل فارسی و روشن‌اند
        e is IllegalStateException -> e.message ?: "خطا"
        // احراز هویت SSH
        e is net.schmizz.sshj.userauth.UserAuthException ->
            "رمز سرور اشتباه است یا ورود root با رمز روی این سرور مجاز نیست. رمز را دوباره بررسی کنید."
        // نشانی و مسیر
        e is java.net.UnknownHostException ->
            "آدرس سرور پیدا نشد؛ املای آدرس و اتصال اینترنت گوشی را بررسی کنید."
        e is java.net.ConnectException || e is java.net.NoRouteToHostException ||
            e is java.net.PortUnreachableException ->
            "اتصال به سرور ممکن نشد؛ ممکن است پورت SSH در فایروال سرور بسته باشد یا آدرس اشتباه باشد."
        e is java.net.SocketTimeoutException ->
            "سرور سرِ وقت جواب نداد؛ اتصال شبکه یا فایروال را بررسی کنید و دوباره تلاش کنید."
        // الگوریتم رمزنگاری (نباید بعد از r10 رخ دهد — ولی اگر شد، روشن بگوییم)
        msg.contains("algorithm") || msg.contains("x25519") ->
            "این دستگاه از رمزنگاری موردنیاز پشتیبانی نمی‌کند؛ نسخهٔ اندروید را به‌روز کنید یا با پشتیبانی تماس بگیرید."
        msg.contains("auth fail") ->
            "رمز سرور اشتباه است؛ دوباره بررسی کنید."
        else ->
            e.message?.takeIf { it.isNotBlank() } ?: "خطای غیرمنتظره — دوباره تلاش کنید."
    }
}

private fun isConnectivityIssue(errorText: String): Boolean =
    errorText.contains("فایروال") || errorText.contains("پیدا نشد") ||
        errorText.contains("جواب نداد") || errorText.contains("اتصال به سرور ممکن نشد")

@Composable
private fun FormView(
    name: String,
    host: String,
    sshPort: String,
    agentPort: String,
    password: String,
    showPassword: Boolean,
    githubToken: String,
    advanced: Boolean,
    error: String?,
    onName: (String) -> Unit,
    onHost: (String) -> Unit,
    onSshPort: (String) -> Unit,
    onAgentPort: (String) -> Unit,
    onPassword: (String) -> Unit,
    onGithubToken: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onAdvanced: (Boolean) -> Unit,
    onInstall: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // کارت خوشامد — قرارداد را در یک نگاه می‌گوید
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.VpnKey,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "سرور تازه دارید؟ فقط دو چیز لازم است",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "آدرس سرور و رمز آن را وارد کنید؛ پرستو خودش وصل می‌شود، پل را نصب و همه‌چیز را امن می‌کند. شما هیچ‌وقت وارد سرور نمی‌شوید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text("نام دلخواه (اختیاری)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = host,
            onValueChange = onHost,
            label = { Text("آدرس سرور (IP یا دامنه)") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Dns, null, Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPassword,
            label = { Text("رمز سرور (root) — فقط همین یک بار") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp)) },
            supportingText = {
                Text(
                    "فرمان مالک (2026-11-09): رمز یک بار گرفته می‌شود و در گاوصندوق رمزنگاری‌شدهٔ همین گوشی (کلید سخت‌افزاری) قفل می‌شود — از این پس به‌روزرسانی پل و بقیهٔ کارها بدون هیچ پرسشی انجام می‌شوند.",
                    style = MaterialTheme.typography.labelSmall,
                )
            },
            trailingIcon = {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (showPassword) "پنهان کردن رمز" else "نمایش رمز",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // فرمان مالک (2026-11-09): توکن گیت‌هاب اختیاری از مالک — یک‌جای مناسب سرور
        OutlinedTextField(
            value = githubToken,
            onValueChange = onGithubToken,
            label = { Text("توکن گیت‌هاب (اختیاری)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Rounded.Key, null, Modifier.size(18.dp)) },
            supportingText = {
                Text(
                    "برای دسترسی عامل هوش مصنوعی به گیت‌هاب. توصیه: توکن Fine-grained فقط برای مخازن پروستو با Contents: Read and Write و تاریخ انقضا. توکن فقط یک بار برای «سرور خودتان» فرستاده و آن‌جا با دسترسی ۶۰۰ نگه داشته می‌شود؛ روی این گوشی ذخیره نمی‌شود.",
                    style = MaterialTheme.typography.labelSmall,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // تنظیمات پیشرفته — تا وقتی باز نشده دیده نمی‌شوند
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onAdvanced(!advanced) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "تنظیمات پیشرفته",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = advanced) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = sshPort,
                        onValueChange = { onSshPort(it.filter { c -> c.isDigit() }.take(5)) },
                        label = { Text("پورت SSH") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = agentPort,
                        onValueChange = { onAgentPort(it.filter { c -> c.isDigit() }.take(5)) },
                        label = { Text("پورت پل") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Text(
            "نکتهٔ امنیتی: رمز سرور با AES-256 و کلید سخت‌افزاری همین گوشی قفل می‌شود و فقط برای نصب و به‌روزرسانی خودکار، داخل کانال رمزنگاری‌شدهٔ SSH مصرف می‌شود؛ توکن‌ها را خودِ گوشی می‌سازد و سرور فقط هش‌شان را نگه می‌دارد. اگر سرور قبلاً پل داشته باشد، مالکیت بی‌سروصدا به این گوشی منتقل و توکن‌های قبلی باطل می‌شوند.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PrimaryCta(
            text = "نصب پل و اتصال",
            icon = Icons.Rounded.CloudUpload,
            enabled = host.isNotBlank() && password.isNotBlank(),
            onClick = onInstall,
        )

        error?.let {
            InfoBanner(kind = BannerKind.Error, title = "نصب ناتمام ماند", text = it)
            Text(
                if (isConnectivityIssue(it)) {
                    "می‌توانید از یک رایانه هم امتحان کنید: ping به همین آدرس، بگیرد یعنی مسیر شبکه باز است و مشکل از رمز یا پورت است."
                } else {
                    "اصلاح کنید و دوباره تلاش کنید؛ اطلاعات شما در همین صفحه باقی می‌ماند."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------- حالت در حال نصب: مراحل + ترمینال زنده ----------

@Composable
private fun RunningView(
    phase: String,
    logs: List<String>,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    val currentStep = stepIndexFor(phase)
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        phase.ifEmpty { "آماده‌سازی" },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    "این صفحه را نبندید؛ نصب معمولاً کمتر از یک دقیقه طول می‌کشد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionLabel("مراحل نصب")
        Column {
            PROVISION_STEPS.forEachIndexed { i, (icon, label) ->
                val done = i < currentStep
                val now = i == currentStep
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 5.dp),
                ) {
                    when {
                        done -> Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        now -> Box(
                            Modifier
                                .size(18.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(MaterialTheme.colorScheme.onPrimary, CircleShape),
                            )
                        }
                        else -> Box(
                            Modifier
                                .size(18.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = if (done || now) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        style = if (now) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        color = when {
                            now -> MaterialTheme.colorScheme.primary
                            done -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        SectionLabel("لاگ زندهٔ سرور")
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(TerminalBg, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            items(logs.size) { i ->
                val line = logs[i]
                Text(
                    line,
                    color = if (line.startsWith("خطا")) TerminalError else TerminalText,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}

// ---------- حالت موفق: جشن کوتاه + جزئیات امنیتی ----------

@Composable
private fun DoneView(
    res: ServerProvisioner.ProvisionResult,
    saving: Boolean,
    error: String?,
    onSave: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .size(84.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("سرور آماده است!", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "پل نصب شد، گواهی ثبت شد و تست نهایی هم موفق بود.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DetailRow("میزبان", res.hostname)
                DetailRow("نسخهٔ پل", "v" + res.agentVersion)
                DetailRow("آدرس پل", res.agentUrl, mono = true)
                DetailRow(
                    "پین گواهی TLS",
                    "${res.certSha256.take(12)}…${res.certSha256.takeLast(6)}",
                    mono = true,
                )
                DetailRow(
                    "کلید میزبان SSH",
                    "${res.sshHostKeyFp.take(12)}…${res.sshHostKeyFp.takeLast(6)}",
                    mono = true,
                )
                res.githubTokenSet?.let { gh ->
                    DetailRow(
                        "توکن گیت‌هاب",
                        when {
                            gh && res.githubLogin.isNotBlank() -> "ذخیره شد (حساب: ${res.githubLogin})"
                            gh -> "ذخیره شد"
                            else -> "ذخیره نشد — از داشبورد اضافه کنید"
                        },
                    )
                }
            }
        }
        InfoBanner(
            kind = BannerKind.Success,
            text = "توکن ادمین همین حالا روی این گوشی ساخته شد و فقط در حافظهٔ رمزنگاری‌شده (Android Keystore) ماند. رمز سرور هیچ‌جا ذخیره نشد — برای نصب دوباره همان رمز را دوباره وارد می‌کنید.",
        )
        PrimaryCta(
            text = if (saving) "در حال ذخیره…" else "ذخیره و ادامه",
            icon = Icons.Rounded.Check,
            loading = saving,
            enabled = !saving,
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
