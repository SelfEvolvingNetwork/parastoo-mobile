package network.selfevolving.parastoo.mobile

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import network.selfevolving.parastoo.mobile.core.security.BiometricGate
import network.selfevolving.parastoo.mobile.ui.AppRoot
import network.selfevolving.parastoo.mobile.ui.theme.ParastooTheme

/**
 * اکتیویتی یکتا. ورود اولین بار (و هر بار باز شدن اپ) با قفل گوشی —
 * قرارداد مالک: اطلاعات سرور فقط یک بار وارد می‌شود، بعد از آن بیومتریک/PIN.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ParastooTheme {
                val hasGate = BiometricGate.available(this)
                var unlocked by remember { mutableStateOf(!hasGate) }
                if (unlocked) {
                    AppRoot()
                } else {
                    LockScreen(
                        hasGate = hasGate,
                        onStartUnlock = {
                            BiometricGate.unlock(
                                this,
                                onSuccess = { unlocked = true },
                                onError = { },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LockScreen(hasGate: Boolean, onStartUnlock: () -> Unit) {
    LaunchedEffect(Unit) {
        if (hasGate) onStartUnlock()
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = "قفل",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text("پرستو موبایل قفل است", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                if (hasGate) "برای ورود، قفل گوشی را باز کنید"
                else "برای امنیت توصیه می‌شود قفل دستگاه (PIN/بیومتریک) فعال کنید",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            if (hasGate) {
                Button(onClick = onStartUnlock) { Text("باز کردن با قفل گوشی") }
            }
        }
    }
}
