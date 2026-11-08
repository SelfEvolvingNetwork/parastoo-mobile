package network.selfevolving.parastoo.mobile.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import network.selfevolving.parastoo.mobile.R

/**
 * فونت وزیرمتن — دستور مالک (2026-11-08): تایپوگرافی فارسیِ همه‌جای اپ با
 * وزیرمتن. چهار وزن باندل‌شده در res/font؛ هیچ متن فارسی‌ای خارج از این
 * خانواده رندر نمی‌شود (قرارداد در Type.kt اعمال شده است).
 */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)
