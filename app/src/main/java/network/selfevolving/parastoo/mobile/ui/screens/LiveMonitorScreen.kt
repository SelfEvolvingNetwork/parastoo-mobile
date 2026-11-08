package network.selfevolving.parastoo.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import network.selfevolving.parastoo.mobile.core.model.AuditEvent
import network.selfevolving.parastoo.mobile.core.net.SseAuditStream
import network.selfevolving.parastoo.mobile.ui.components.BackTopBar
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
        // نوار وضعیت اتصال
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (connected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (connected) "● زنده — هر رویداد پل همین لحظه می‌آید"
                    else "○ در حال اتصال… (خودکار دوباره وصل می‌شود)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { autoScroll = !autoScroll }) {
                    Icon(
                        if (autoScroll) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (autoScroll) "توقف اسکرول" else "اسکرول خودکار",
                    )
                }
            }
        }
        // فیلترها
        Row(
            Modifier.padding(horizontal = 16.dp),
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
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.Bolt, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    "هنوز رویدادی نیامده. به محض این‌که عامل (یا خود اپ) چیزی را روی سرور بزند، همین‌جا زنده می‌بینید.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
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
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ev.isExec) Icons.Filled.Terminal else Icons.Filled.Bolt,
                    null,
                    Modifier.height(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.padding(4.dp))
                Text(
                    ev.action,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                if (ev.exitCode != null && ev.exitCode != 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.error) { Text("exit ${ev.exitCode}") }
                }
                if (ev.timedOut) {
                    Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text("timeout") }
                }
            }
            Text(
                "${ev.ts.take(19).replace('T', ' ')}  ·  ${ev.actor}  ·  ${ev.ip}" +
                    (ev.durationMs?.let { "  ·  ${it}ms" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ev.cmdPreview.isNotBlank()) {
                Text(
                    "$ ${ev.cmdPreview}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                )
            }
            if (expanded) {
                if (ev.stdoutPreview.isNotBlank()) {
                    Text("— خروجی —", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        ev.stdoutPreview,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (ev.stderrPreview.isNotBlank()) {
                    Text("— خطا —", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    Text(
                        ev.stderrPreview,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (ev.error.isNotBlank()) {
                    Text(
                        "error: ${ev.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
