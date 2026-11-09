package network.selfevolving.parastoo.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * نشان پرستو: دایرهٔ سبزِ لانه با پرنده — امضای بصری اپ (نسخهٔ بدنه).
 * drawable از :foundation می‌آید تا هستهٔ خارجی هم قرارداداً همین را دارد
 * (در runtime نسخهٔ همین کلاسِ بدنه با parent-first اولویت دارد).
 */
@Composable
fun SwallowBadge(modifier: Modifier = Modifier, size: Dp = 104.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(network.selfevolving.parastoo.mobile.foundation.R.drawable.ic_swallow),
            contentDescription = "پرستو",
            modifier = Modifier.size(size * 0.52f),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
