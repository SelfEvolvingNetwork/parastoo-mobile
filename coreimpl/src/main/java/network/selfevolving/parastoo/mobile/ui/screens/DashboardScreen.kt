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
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.selfevolving.parastoo.mobile.core.config.CoreConfigStore
import network.selfevolving.parastoo.mobile.core.model.HostStatus
import network.selfevolving.parastoo.mobile.core.net.AccessAgentApi
import network.selfevolving.parastoo.mobile.core.provision.AutoUpdater
import network.selfevolving.parastoo.mobile.core.session.SessionRecovery
import network.selfevolving.parastoo.mobile.ui.components.ActionTile
import network.selfevolving.parastoo.mobile.ui.components.BackTopBar
import network.selfevolving.parastoo.mobile.ui.components.EmptyState
import network.selfevolving.parastoo.mobile.ui.components.ServerSession
import network.selfevolving.parastoo.mobile.ui.components.StatCard
import network.selfevolving.parastoo.mobile.ui.components.StatusDot
import network.selfevolving.parastoo.mobile.ui.components.rememberSession

/**
 * داشبورد سرور — بازنگری چیدمان (فرمان مالک 2026-11-09: «از اول روی چیدمان
 * بررسی و طراحی کن»). قرارداد تازه: «هر کار، خانهٔ خودش» — داشبورد فقط
 * *یک‌نگاهیِ وضعیت* و *ناوبری* است:
 *   • کارت قهرمان + آمار — وضعیت زنده، هر ۱۵ ثانیه
 *   • کاشی‌ها: مانیتور زنده، ایجنت، مدیریت پرستو، تنظیمات سرور
 * هیچ تنظیمی، کلیدی، توکنی، به‌روزرسانی‌ای یا منطقهٔ خطر این‌جا نیست — همه به
 * خانهٔ خودشان رفتند: «تنظیمات سرور» و «تنظیمات اپ».
 * وظایف بی‌صدای این صفحه: تازه‌سازی هستهٔ هات + بررسی آپدیت خودکار پل.
 */
@Composable
fun DashboardScreen(
    serverId: Long,
    onMonitor: () -> Unit,
    onAgent: () -> Unit,
    onLifecycle: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val session = rememberSession(serverId)
    var status by remember { mutableStateOf<HostStatus?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // هستهٔ هات — واکشی بی‌سروصدا از همین پل (v0.4.12)؛ هر خطا = ادامه با
    // نسخهٔ فعلی؛ فلگ/متن با تغییرش صفحه خودکار تازه می‌شود
    LaunchedEffect(session?.profile?.id) {
        val s = session ?: return@LaunchedEffect
        CoreConfigStore.refresh { s.api.coreConfig() }
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
        }
    }

    LaunchedEffect(session) {
        var sess = session ?: return@LaunchedEffect
        while (true) {
            loading = true
            try {
                status = withContext(Dispatchers.IO) { sess.api.status() }
                error = null
            } catch (e: AccessAgentApi.ApiException) {
                // v0.5.6 — فرمان مالک ۲۰۲۶-۱۱-۱۲ («الگوی مخرب آپدیت»): 401 یعنی
                // توکنِ گوشی با سرور هم‌گام نیست؛ بازیابی درون‌سیستمی (رمز SSH
                // گاوصندوق + کلید میزبان پین‌شده) — نه حذف/افزودن دوباره با رمز.
                var recovered = false
                if (e.code == 401 || e.code == 403) {
                    val r = withContext(Dispatchers.IO) {
                        SessionRecovery.recover(context, sess.profile)
                    }
                    if (r != null) {
                        sess = ServerSession(
                            r.profile,
                            AccessAgentApi(r.profile.url, r.profile.certSha256, r.adminToken),
                            r.adminToken,
                        )
                        recovered = true
                    }
                }
                if (!recovered) {
                    error = e.message ?: e.javaClass.simpleName
                }
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
            delay(15000)
        }
    }

    val s = session
    val st = status
    // به‌روزرسانی خودکار پل — کارتش به «تنظیمات سرور» رفت ولی حلقهٔ خودکار
    // باید از باز شدن داشبورد ادامه یابد (فرمان مالک: بدون هیچ پرسشی)
    LaunchedEffect(s?.profile?.id, st?.version) {
        val sess = s ?: return@LaunchedEffect
        val v = st?.version ?: return@LaunchedEffect
        AutoUpdater.checkAndMaybeUpdate(context, sess.profile.id, sess.profile, v)
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(
            title = s?.profile?.name ?: "سرور",
            subtitle = s?.profile?.url,
            onBack = onBack,
            actions = {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "تنظیمات سرور")
                }
            },
        )
        if (s == null) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                EmptyState(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    icon = Icons.Rounded.Dns,
                    title = "این سرور آماده نیست",
                    body = "بازیابی خودکار (رمزِ ذخیره‌شدهٔ همین گوشی) هم ممکن نشد — رمز SSH روی سرور عوض شده یا در گاوصندوق نیست. حذف و افزودن دوباره آخرین راه است؛ رمز فقط همان یک بار پرسیده می‌شود.",
                )
            }
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
                        icon = Icons.Rounded.Build,
                        title = "مدیریت پرستو",
                        subtitle = "نصب، حذف کامل، نصب مجدد و به‌روزرسانی — بدون ورود به سرور",
                        onClick = onLifecycle,
                    )
                    ActionTile(
                        icon = Icons.Rounded.Tune,
                        title = "تنظیمات سرور",
                        subtitle = "کلید توقف، توکن‌ها، به‌روزرسانی خودکار پل و منطقهٔ خطر",
                        onClick = onSettings,
                    )

                    Text(
                        "وضعیت هر ۱۵ ثانیه خودکار تازه می‌شود.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
