package network.selfevolving.parastoo.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// پالت تیرهٔ پرستو — سبز لانه روی زمینهٔ ذغالی
private val Bg = Color(0xFF101418)
private val Surface = Color(0xFF161B22)
private val SurfaceVariant = Color(0xFF1E2630)
private val OnBg = Color(0xFFE6EDF3)
private val OnSurfaceVariant = Color(0xFF9BA7B4)
private val Nest = Color(0xFF66D9A5)
private val OnNest = Color(0xFF08130D)
private val Danger = Color(0xFFFF6B6B)
private val Sky = Color(0xFF7AB8FF)
private val Amber = Color(0xFFFFC857)

private val DarkColors = darkColorScheme(
    primary = Nest,
    onPrimary = OnNest,
    primaryContainer = Color(0xFF1E3A2D),
    onPrimaryContainer = Nest,
    secondary = Sky,
    onSecondary = Color(0xFF08121E),
    tertiary = Amber,
    background = Bg,
    onBackground = OnBg,
    surface = Surface,
    onSurface = OnBg,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Danger,
    onError = Color(0xFF2A0A0A),
    errorContainer = Color(0xFF3A1515),
    onErrorContainer = Danger,
    outline = Color(0xFF39434E),
)

@Composable
fun ParastooTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
