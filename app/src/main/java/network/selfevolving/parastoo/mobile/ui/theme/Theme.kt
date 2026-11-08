package network.selfevolving.parastoo.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * پالت «پرستو» — دو حالته:
 *  روشن: زمینهٔ کرمِ سبزفام آرام، کارت‌های ملایم، سبز لانهٔ عمیق برای کنش‌های اصلی
 *  تاریک: ذغالِ سبزفام (ادامهٔ هویت قبلی اپ)، نعنایی درخشان برای کنش‌ها
 * همهٔ نقش‌های سطحی (surfaceContainer*) هم عمداً بازنویسی شده‌اند تا هیچ
 * کامپوننت متریال به رنگ خاکستری پیش‌فرضِ بنفش‌فام برگردد.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF177E5B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFF3E0),
    onPrimaryContainer = Color(0xFF073D2A),
    inversePrimary = Color(0xFF6CDFAC),
    secondary = Color(0xFF4A6357),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE9E0),
    onSecondaryContainer = Color(0xFF10201A),
    tertiary = Color(0xFF9C6F00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE2A0),
    onTertiaryContainer = Color(0xFF312200),
    background = Color(0xFFF0F5F0),
    onBackground = Color(0xFF1A201B),
    surface = Color(0xFFFBFDFB),
    onSurface = Color(0xFF1A201B),
    surfaceVariant = Color(0xFFE2EBE3),
    onSurfaceVariant = Color(0xFF54655B),
    surfaceTint = Color(0xFF177E5B),
    inverseSurface = Color(0xFF2B352E),
    inverseOnSurface = Color(0xFFEDF3EC),
    outline = Color(0xFF748077),
    outlineVariant = Color(0xFFC3D0C7),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    surfaceDim = Color(0xFFDCE3DC),
    surfaceBright = Color(0xFFFBFDFB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7FAF7),
    surfaceContainer = Color(0xFFF0F5F0),
    surfaceContainerHigh = Color(0xFFE9EFE9),
    surfaceContainerHighest = Color(0xFFE2E9E2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6CDFAC),
    onPrimary = Color(0xFF04371F),
    primaryContainer = Color(0xFF175236),
    onPrimaryContainer = Color(0xFFA5F2CD),
    inversePrimary = Color(0xFF177E5B),
    secondary = Color(0xFF8FC9EE),
    onSecondary = Color(0xFF0A2436),
    secondaryContainer = Color(0xFF27455C),
    onSecondaryContainer = Color(0xFFC6E6FB),
    tertiary = Color(0xFFFFC857),
    onTertiary = Color(0xFF3E2F00),
    tertiaryContainer = Color(0xFF5B4500),
    onTertiaryContainer = Color(0xFFFFE2A0),
    background = Color(0xFF0E1310),
    onBackground = Color(0xFFE0E8E1),
    surface = Color(0xFF151B17),
    onSurface = Color(0xFFE0E8E1),
    surfaceVariant = Color(0xFF202B24),
    onSurfaceVariant = Color(0xFF9BAFA2),
    surfaceTint = Color(0xFF6CDFAC),
    inverseSurface = Color(0xFFE0E8E1),
    inverseOnSurface = Color(0xFF2B352E),
    outline = Color(0xFF46554B),
    outlineVariant = Color(0xFF2A362F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceDim = Color(0xFF0E1310),
    surfaceBright = Color(0xFF3A453E),
    surfaceContainerLowest = Color(0xFF090D0B),
    surfaceContainerLow = Color(0xFF161C18),
    surfaceContainer = Color(0xFF1A211C),
    surfaceContainerHigh = Color(0xFF242C26),
    surfaceContainerHighest = Color(0xFF2F3731),
)

/** گوشه‌های نرم و دوستانه — کارت‌ها ۱۸، دیالوگ‌ها ۲۸ */
private val ParastooShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ParastooTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = ParastooTypography,
        shapes = ParastooShapes,
        content = content,
    )
}
