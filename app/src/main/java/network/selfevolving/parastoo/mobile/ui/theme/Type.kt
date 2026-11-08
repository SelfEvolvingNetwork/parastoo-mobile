package network.selfevolving.parastoo.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * تایپوگرافی فارسی‌دوست: وزیرمتن روی همهٔ سبک‌ها (دستور مالک 2026-11-08)،
 * اندازه‌های کمی بزرگ‌تر از پیش‌فرض، وزن‌های واضح و ارتفاع خط سخاوتمندانه
 * (حروف فارسی جا برای دُم و اعراب دارند). هیچ سبکی بدون وزیرمتن نیست.
 */
private fun parastoo(weight: FontWeight, size: androidx.compose.ui.unit.TextUnit, line: androidx.compose.ui.unit.TextUnit) = TextStyle(
    fontFamily = Vazirmatn,
    fontWeight = weight,
    fontSize = size,
    lineHeight = line,
)

val ParastooTypography = Typography(
    displaySmall = parastoo(FontWeight.Bold, 30.sp, 42.sp),
    headlineMedium = parastoo(FontWeight.Bold, 26.sp, 38.sp),
    headlineSmall = parastoo(FontWeight.Bold, 21.sp, 32.sp),
    titleLarge = parastoo(FontWeight.Bold, 19.sp, 28.sp),
    titleMedium = parastoo(FontWeight.SemiBold, 16.sp, 26.sp),
    titleSmall = parastoo(FontWeight.SemiBold, 14.sp, 22.sp),
    bodyLarge = parastoo(FontWeight.Normal, 15.sp, 26.sp),
    bodyMedium = parastoo(FontWeight.Normal, 13.5.sp, 23.sp),
    bodySmall = parastoo(FontWeight.Normal, 12.sp, 20.sp),
    labelLarge = parastoo(FontWeight.SemiBold, 13.sp, 20.sp),
    labelMedium = parastoo(FontWeight.SemiBold, 12.sp, 17.sp),
    labelSmall = parastoo(FontWeight.Medium, 10.5.sp, 15.sp),
)
