package network.selfevolving.parastoo.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import network.selfevolving.parastoo.mobile.core.model.AuditEvent
import network.selfevolving.parastoo.mobile.core.net.SseAuditStream
import network.selfevolving.parastoo.mobile.ui.components.BackTopBar
import network.selfevolving.parastoo.mobile.ui.components.EmptyState
import network.selfevolving.parastoo.mobile.ui.components.StatusDot
import network.selfevolving.parastoo.mobile.ui.components.rememberSession

private val FILTERS = listOf("همه", "exec", "admin", "github", "auth")

/**
 * مانیتور زندهٔ جریان کاری عامل — تماماً سمت نرم‌افزار:
 * پل همه‌چیز را در audit.jsonl ثبت می‌کند و این صفحه فقط همان را زنده نشان
 * می‌دهد (SSE). عامل نه گزارش می‌سازد و نه هیچ درخواست اضافه‌ای به او می‌رسد.
 */
@Composable
fun LiveMonitorScreen(serverId: Long, onBack: () -> Unit) {
    val session = rememberSession(serverId)
    val events = remember { mutableStateListOf<AuditEvent>() }
    var connected by remember { mutableStateOf(false) }
    var autoScroll by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf("همه") }
    val listState = rememberLazyListState()

    DisposableEffect(session) {
        val stream = session?.let {
            SseAuditStream(it.profile.url, it.profile.certSha256, it.adminToken)
        }
        stream?.start(
            backfill = 60,
            onEvent = { ev ->
                events.add(ev)
                if (events.size > 400) events.removeRange(0, 100)
            },
            onConnected = { connected = it },
        )
        onDispose { stream?.stop() }
    }

    val shown = remember(events.size, filter) {
        if (filter == "همه") events.toList() else events.filter { it.category == filter }
    }

    androidx.compose.runtime.LaunchedEffect(shown.size, autoScroll) {
        if (autoScroll && shown.isNotEmpty()) {
            listState.animateScrollToItem(shown.size - 1)
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(
            title = "مانیتور زنده",
            subtitle = session?.profile?.name,
            onBack = onBack,
        )

        // نشانگر اتصال — کپسول نبض‌دار
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(
                    if (connected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(50),
                )
                .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(ok = connected)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (connected) "زنده — هر رویداد پل همین لحظه می‌آید"
                    else "در حال اتصال… (خودکار دوباره وصل می‌شود)",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (connected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (autoScroll) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (autoScroll) "توقف اسکرول" else "اسکرول خودکار",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { autoScroll = !autoScroll }
                        .padding(7.dp),
                    tint = if (connected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // فیلترها
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FILTERS.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = { Text(if (f == "همه") "همه" else f, fontFamily = FontFamily.Monospace) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                EmptyState(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    icon = Icons.Rounded.Bolt,
                    title = "هنوز رویدادی نیامده",
                    body = "به محض این‌که عامل (یا خود اپ) چیزی را روی سرور بزند، همین‌جا زنده می‌بینید.",
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.raw.hashCode().toLong() }) { ev ->
                    EventCard(ev)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun EventCard(ev: AuditEvent) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = if (ev.isError) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(
                            if (ev.isError) MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (ev.isExec) Icons.Rounded.Terminal else Icons.Rounded.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = if (ev.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        ev.action,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${ev.ts.take(19).replace('T', ' ')}  ·  ${ev.actor}  ·  ${ev.ip}" +
                            (ev.durationMs?.let { "  ·  ${it}ms" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ev.isError) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (ev.exitCode != null && ev.exitCode != 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text("exit ${ev.exitCode}", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (ev.timedOut) {
                    Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                        Text("timeout", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiary)
                    }
                }
            }
            if (ev.cmdPreview.isNotBlank()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (ev.isError) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        "$ ${ev.cmdPreview}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (expanded) {
                if (ev.stdoutPreview.isNotBlank()) {
                    Text(
                        "— خروجی —",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ev.isError) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        ev.stdoutPreview,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (ev.stderrPreview.isNotBlank()) {
                    Text(
                        "— خطا —",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ev.isError) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.error,
                    )
                    Text(
                        ev.stderrPreview,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (ev.isError) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.error,
                    )
                }
                if (ev.error.isNotBlank()) {
                    Text(
                        "error: ${ev.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ev.isError) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
