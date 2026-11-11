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
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.selfevolving.parastoo.mobile.core.model.LifecycleStatus
import network.selfevolving.parastoo.mobile.ui.components.BackTopBar
import network.selfevolving.parastoo.mobile.ui.components.CodeBlock
import network.selfevolving.parastoo.mobile.ui.components.DetailRow
import network.selfevolving.parastoo.mobile.ui.components.rememberSession

/**
 * مدیریت پرستو از گوشی — نصب، حذف کامل و نصب مجدد = فقط با فرمان صریح کاربر
 * (هر عملیات تخریبی با تایپِ کلمهٔ تأیید). به‌روزرسانی = خودکار و بدون پرسش
 * (فرمان مالک 2026-11-09: «بروزرسانی نباید پرسیده شود») — دکمهٔ «به‌روزرسانی فوری»
 * هم بدون تأیید می‌چرخد؛ تایمر خودکار سرور هم پس از فرمان 2026-11-09 همیشه
 * کانال گیت‌هاب را دنبال می‌کند. پیشرفت زنده از لاگ همان عملیات روی سرور می‌آید.
 */
@Composable
fun LifecycleScreen(serverId: Long, onBack: () -> Unit) {
    val session = rememberSession(serverId)
    val scope = rememberCoroutineScope()
    var st by remember { mutableStateOf<LifecycleStatus?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var pendingConfirm by remember { mutableStateOf<String?>(null) }
    var showLog by remember { mutableStateOf(false) }

    fun fire(action: String) {
        val api = session?.api ?: return
        scope.launch {
            try {
                withContext(Dispatchers.IO) { api.parastooLifecycleAction(action) }
                actionError = null
            } catch (e: Exception) {
                actionError = "${faAction(action)} انجام نشد: ${e.message}"
            }
        }
    }

    LaunchedEffect(session) {
        val api = session?.api ?: return@LaunchedEffect
        while (true) {
            try {
                st = withContext(Dispatchers.IO) { api.parastooLifecycle() }
                error = null
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
            delay(if (st?.busy == true) 2500 else 6000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(title = "مدیریت پرستو", subtitle = session?.profile?.name, onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val s = st
            when {
                error != null -> Text(
                    "ارتباط با سرور برقرار نشد: $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                s == null -> Row(
                    Modifier.fillMaxWidth().padding(top = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
                else -> {
                    // ---------- وضعیت ----------
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("وضعیت پرستو", style = MaterialTheme.typography.titleMedium)
                            if (s.busy) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "${faAction(s.action)} در جریان است — این صفحه خودش تازه می‌شود",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            } else if (s.installed) {
                                Text(
                                    "نصب است" + if (s.version.isNotBlank()) " — نسخهٔ ${s.version}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                DetailRow("مسیر نصب", s.installPath.ifEmpty { "نامشخص" }, mono = true)
                            } else {
                                Text(
                                    "نصب نیست",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // ---------- سلامت استک (پل v0.7.2 — «بعد از ری‌استارت بالا آمده؟») ----------
                    if (s.stackChecked) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("سلامت استک پرستو", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (s.stackCoreHealthz) "هستهٔ پرستو پاسخ سلامت داده — استک بالا است."
                                    else "هستهٔ پرستو هنوز پاسخ سلامت نمی‌دهد؛ چند لحظه بعد دوباره نگاه کنید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (s.stackCoreHealthz) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                                )
                                s.stackContainers.sortedBy { it.first }.forEach { (name, status) ->
                                    DetailRow(
                                        name.removePrefix("parastoo-"),
                                        status,
                                        mono = true,
                                    )
                                }
                                if (s.stackProjectContainersUp > 0) {
                                    Text(
                                        "${s.stackProjectContainersUp} کانتینر پروژهٔ کاربران هم در حال اجراست",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // ---------- نتیجهٔ آخرین عملیات ----------
                    if (!s.busy && s.exitCode != null) {
                        val ok = s.okLast == true
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        when {
                                            ok && s.action == "uninstall" -> "نقطهٔ صفر — همه‌چیز پاک شد"
                                            ok -> "${faAction(s.action)} انجام شد"
                                            else -> "${faAction(s.action)} ناکام ماند"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                }
                                if (ok && s.action == "uninstall") {
                                    Text(
                                        "سرور مثل روز اول است؛ هیچ اثری و گردی باقی نمانده. " +
                                            "نصب تازه با دامنهٔ پیش‌فرض zeolitecenter.ir از همین صفحه شروع می‌شود.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { showLog = !showLog }) {
                                    Text(if (showLog) "بستن گزارش" else "نمایش گزارش")
                                }
                                if (showLog) CodeBlock(s.logText.ifEmpty { "لاگی نیست" })
                            }
                        }
                    }

                    // ---------- کنش‌ها ----------
                    if (s.busy) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("گزارش زنده", style = MaterialTheme.typography.titleSmall)
                                CodeBlock(s.logText.ifEmpty { "…" })
                            }
                        }
                    } else if (s.installed) {
                        Button(
                            onClick = { fire("update") },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("به‌روزرسانی فوری — بدون پرسش")
                        }
                        OutlinedButton(
                            onClick = { pendingConfirm = "reinstall" },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("نصب مجدد")
                        }
                        OutlinedButton(
                            onClick = { pendingConfirm = "uninstall" },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(
                                Icons.Rounded.DeleteForever, contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("حذف کامل", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Button(
                            onClick = { pendingConfirm = "install" },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("نصب پرستو")
                        }
                        Text(
                            "نصب چند دقیقه طول می‌کشد و همه‌چیز را خودش می‌چیند: داکر، دامنه، گواهی و پنل.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    actionError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        "فرمان از همین گوشی می‌رود، اجرا روی خود سرور است؛ همهٔ گام‌ها در گزارش رویدادها ثبت می‌شود.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    pendingConfirm?.let { kind ->
        if (kind == "install") {
            AlertDialog(
                onDismissRequest = { pendingConfirm = null },
                title = { Text("نصب پرستو؟") },
                text = {
                    Text(
                        "نصب تازه روی این سرور انجام می‌شود؛ دامنهٔ پیش‌فرض zeolitecenter.ir برقرار می‌شود " +
                            "و چند دقیقه زمان می‌برد."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { pendingConfirm = null; fire(kind) }) { Text("انجام بده") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingConfirm = null }) { Text("انصراف") }
                },
            )
        } else {
            TypedConfirmDialog(
                kind = kind,
                onConfirm = { pendingConfirm = null; fire(kind) },
                onDismiss = { pendingConfirm = null },
            )
        }
    }
}

@Composable
private fun TypedConfirmDialog(kind: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val (title, body, word) = when (kind) {
        "uninstall" -> Triple(
            "حذف کامل پرستو",
            "همهٔ چیز پرستو پاک می‌شود: پنل، گیت، سایت‌ها، پروژه‌ها، داده‌ها، گواهی‌ها و هویت دامنه — " +
                "سرور به نقطهٔ صفر می‌رسد و هیچ اثری و گردی باقی نمی‌ماند. بازگشتی وجود ندارد. " +
                "برای تأیید بنویسید: حذف",
            "حذف",
        )
        else -> Triple(
            "نصب مجدد پرستو",
            "اول حذف کامل، سپس نصب تازه؛ داده‌ها و سایت‌ها پاک می‌شوند. برای تأیید بنویسید: نصب مجدد",
            "نصب مجدد",
        )
    }
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
            val ok = typed.trim() == word
            TextButton(enabled = ok, onClick = onConfirm) {
                Text("انجام بده", color = if (ok) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

/** عنوان فارسی کوتاه هر عملیات — قرارداد مالک: متن اپ خلاصه و شفاف */
private fun faAction(a: String): String = when (a) {
    "install" -> "نصب"
    "uninstall" -> "حذف کامل"
    "reinstall" -> "نصب مجدد"
    "update" -> "به‌روزرسانی"
    else -> a
}
