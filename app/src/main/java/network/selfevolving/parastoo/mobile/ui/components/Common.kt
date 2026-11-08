package network.selfevolving.parastoo.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import network.selfevolving.parastoo.mobile.R
import network.selfevolving.parastoo.mobile.core.data.Db
import network.selfevolving.parastoo.mobile.core.data.ServerProfileEntity
import network.selfevolving.parastoo.mobile.core.net.AccessAgentApi
import network.selfevolving.parastoo.mobile.core.security.SecretVault
import network.selfevolving.parastoo.mobile.ui.theme.TerminalBg
import network.selfevolving.parastoo.mobile.ui.theme.TerminalText

/** نشست سرور: پروفایل + کلاینت آماده — همهٔ صفحه‌ها از همین می‌سازند */
data class ServerSession(
    val profile: ServerProfileEntity,
    val api: AccessAgentApi,
    val adminToken: String,
)

@Composable
fun rememberSession(serverId: Long): ServerSession? {
    val context = LocalContext.current
    var session by remember(serverId) { mutableStateOf<ServerSession?>(null) }
    LaunchedEffect(serverId) {
        val p = withContext(Dispatchers.IO) { Db.get().serverDao().byId(serverId) }
            ?: return@LaunchedEffect
        val token = SecretVault.getAdminToken(context, p.id)
        if (token.isNotEmpty()) {
            session = ServerSession(p, AccessAgentApi(p.url, p.certSha256, token), token)
        }
    }
    return session
}

/** نشان پرستو: دایرهٔ سبزِ لانه با پرنده — امضای بصری اپ */
@Composable
fun SwallowBadge(modifier: Modifier = Modifier, size: Dp = 104.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_swallow),
            contentDescription = "پرستو",
            modifier = Modifier.size(size * 0.52f),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, subtitle: String? = null, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

/** نقطهٔ وضعیت: سبزِ نبض‌دار وقتی همه‌چیز زنده است، خاکستری ساکن وقتی نه */
@Composable
fun StatusDot(ok: Boolean, modifier: Modifier = Modifier) {
    val color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val transition = rememberInfiniteTransition(label = "statusDot")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Reverse),
        label = "statusDotAlpha",
    )
    Box(
        modifier
            .size(9.dp)
            .alpha(if (ok) alpha else 1f)
            .background(color, CircleShape),
    )
}

/** دکمهٔ اصلی اپ: بلند، گرد و دعوت‌کننده */
@Composable
fun PrimaryCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** برچسب بخش‌ها: کوچک، کم‌رنگ و واضح */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** کارت آمار داشبورد: برچسب کوچک با آیکن + مقدار درشت */
@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** کاشی کنش در داشبورد: آیکن در قاب گرد + عنوان + توضیح */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** حالت خالی: نشان بزرگ + عنوان + توضیح + دکمهٔ اصلی (اختیاری) + کنش ثانویه (اختیاری) */
@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon == null) {
            SwallowBadge()
        } else {
            Box(
                Modifier
                    .size(104.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(46.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            PrimaryCta(actionLabel, onAction)
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onSecondary) { Text(secondaryLabel) }
        }
    }
}

enum class BannerKind { Info, Success, Warning, Error }

/** بنر پیام: رنگ و آیکن بسته به نوع — جای متن‌های خام خطا/موفقیت */
@Composable
fun InfoBanner(
    kind: BannerKind,
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val bg: Color
    val fg: Color
    val ic: ImageVector
    when (kind) {
        BannerKind.Info -> {
            bg = MaterialTheme.colorScheme.secondaryContainer
            fg = MaterialTheme.colorScheme.onSecondaryContainer
            ic = Icons.Rounded.Info
        }
        BannerKind.Success -> {
            bg = MaterialTheme.colorScheme.primaryContainer
            fg = MaterialTheme.colorScheme.onPrimaryContainer
            ic = Icons.Rounded.CheckCircle
        }
        BannerKind.Warning -> {
            bg = MaterialTheme.colorScheme.tertiaryContainer
            fg = MaterialTheme.colorScheme.onTertiaryContainer
            ic = Icons.Rounded.Warning
        }
        BannerKind.Error -> {
            bg = MaterialTheme.colorScheme.errorContainer
            fg = MaterialTheme.colorScheme.onErrorContainer
            ic = Icons.Rounded.ErrorOutline
        }
    }
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = bg)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(ic, contentDescription = null, modifier = Modifier.size(20.dp), tint = fg)
            Spacer(Modifier.width(10.dp))
            Column {
                if (title != null) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = fg)
                    Spacer(Modifier.height(3.dp))
                }
                Text(text, style = MaterialTheme.typography.bodySmall, color = fg)
            }
        }
    }
}

/** بلوک کد/متن فنی روی زمینهٔ ترمینال تیره (بلوک تحویل و اثرانگشت‌ها) */
@Composable
fun CodeBlock(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(TerminalBg, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Text(
            text,
            color = TerminalText,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

/** سطر «برچسب + مقدار» در کارت‌های اطلاعات */
@Composable
fun DetailRow(label: String, value: String, mono: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = if (mono) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmText,
                    color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
