package network.selfevolving.parastoo.mobile

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import network.selfevolving.parastoo.mobile.body.AppCoreLoader
import network.selfevolving.parastoo.mobile.body.CoreFatalScreen
import network.selfevolving.parastoo.mobile.core.security.BiometricGate
import network.selfevolving.parastoo.mobile.ui.components.SwallowBadge
import network.selfevolving.parastoo.mobile.ui.theme.ParastooTheme

/**
 * اکتیویتی یکتا — بدنهٔ ثابت اپ.
 *
 * وظایف بدنه (و فقط همین‌ها): گیت قفل گوشی، تم/فونت/RTL، و لود هسته.
 * هر رابط کاربری و منطقی در هسته است (AppCoreEntry.App از کلاس‌لودرِ هسته) —
 * قرارداد مالک 2026-11-09: «بدنه یک بار نصب می‌ماند، هسته بی‌نصب بروز می‌شود».
 *
 * لود هسته روی IO انجام می‌شود و نشستِ قفل‌گشایی در companion می‌ماند تا
 * recreate پس از تعویض هسته، گیت را دوباره باز نکند (بدون هیچ دیالوگ).
 */
class MainActivity : AppCompatActivity() {

    companion object {
        /** نشست باز — با recreate می‌ماند (همان پروسه) تا هسته‌سوییچ بی‌صدا باشد */
        @Volatile
        var sessionUnlocked: Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // اپ فارسی‌است — چیدمان همیشه راست‌به‌چپ، مستقل از زبانِ دستگاه
            // (دستور مالک 2026-11-08: متن‌های فارسی باید راست به چپ باشند)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ParastooTheme {
                    val coreState by produceState<AppCoreLoader.Result?>(initialValue = null) {
                        value = withContext(Dispatchers.IO) {
                            AppCoreLoader.load(applicationContext)
                        }
                    }
                    when (val st = coreState) {
                        null -> BootScreen()
                        is AppCoreLoader.Result.Failed -> CoreFatalScreen(st.reason)
                        is AppCoreLoader.Result.Loaded -> {
                            val hasGate = BiometricGate.available(this@MainActivity)
                            var unlocked by remember {
                                mutableStateOf(sessionUnlocked || !hasGate)
                            }
                            if (unlocked) {
                                sessionUnlocked = true
                                // گارد سلامت رندر (درس rev31): اگر پروسه پیش از
                                // اولین فریم موفقِ هسته مُرد (مثل کلاس گمشده در
                                // Compose)، بووتِ بعدی این را شکست می‌بُرد تا
                                // کرش‌لوپ هم به فالبک خودکار برسد
                                AppCoreLoader.markBootPending(
                                    applicationContext, st.revision, st.sourceTag,
                                )
                                st.entry.App()
                                LaunchedEffect(st.revision) {
                                    AppCoreLoader.markRenderOk(applicationContext, st.revision)
                                }
                            } else {
                                LockScreen(
                                    hasGate = hasGate,
                                    onStartUnlock = {
                                        BiometricGate.unlock(
                                            this@MainActivity,
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
        }
    }
}

/** لحظهٔ لود هسته — همان هویت بصری قفل صفحه */
@Composable
private fun BootScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SwallowBadge(size = 116.dp)
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
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SwallowBadge(size = 116.dp)
            Spacer(Modifier.height(22.dp))
            Text("پرستو موبایل", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "سرورتان — امن، ساده و همیشه همراه",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            if (hasGate) {
                Button(onClick = onStartUnlock, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("باز کردن با قفل گوشی")
                }
            } else {
                Text(
                    "برای امنیت توکن‌ها، قفل دستگاه (PIN/بیومتریک) را فعال کنید",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
