package network.selfevolving.parastoo.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import network.selfevolving.parastoo.mobile.core.provision.AppUpdater
import network.selfevolving.parastoo.mobile.ui.screens.AgentScreen
import network.selfevolving.parastoo.mobile.ui.screens.AppSettingsScreen
import network.selfevolving.parastoo.mobile.ui.screens.DashboardScreen
import network.selfevolving.parastoo.mobile.ui.screens.LifecycleScreen
import network.selfevolving.parastoo.mobile.ui.screens.LiveMonitorScreen
import network.selfevolving.parastoo.mobile.ui.screens.ProvisionScreen
import network.selfevolving.parastoo.mobile.ui.screens.ServerSettingsScreen
import network.selfevolving.parastoo.mobile.ui.screens.ServersScreen

/**
 * نقشهٔ ناوبری — بازنگری چیدمان (فرمان مالک 2026-11-09: «هر کار، خانهٔ خودش»):
 *   خانه (servers) ─► داشبورد (dashboard/{id}) ─► مانیتور/ایجنت/مدیریت پرستو
 *        │                                    └─► تنظیمات سرور (serversettings/{id})
 *        └─► تنظیمات اپ (appsettings)
 * تنظیمات اپ کلی است (به‌روزرسانی خود اپ، امنیت، درباره) و تنظیمات سرور
 * مالِ همان سرور (کلید توقف، توکن‌ها، آپدیت پل، منطقهٔ خطر).
 */
@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val context = LocalContext.current

    // به‌روزرسانی خودکار خود اپ — بررسی بی‌صدا در هر شروعِ اپ (با فاصلهٔ
    // حداقلی داخلی خودش)؛ کارت وضعیتش در «تنظیمات اپ» است
    LaunchedEffect(Unit) { AppUpdater.check(context) }

    NavHost(navController = nav, startDestination = "servers") {
        composable("servers") {
            ServersScreen(
                onOpen = { id -> nav.navigate("dashboard/$id") },
                onAddProvision = { nav.navigate("provision") },
                onOpenSettings = { nav.navigate("appsettings") },
            )
        }
        composable("provision") {
            ProvisionScreen(onDone = { nav.popBackStack() })
        }
        composable("appsettings") {
            AppSettingsScreen(onBack = { nav.popBackStack() })
        }
        composable("dashboard/{id}") { entry ->
            val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            DashboardScreen(
                serverId = id,
                onMonitor = { nav.navigate("monitor/$id") },
                onAgent = { nav.navigate("agent/$id") },
                onLifecycle = { nav.navigate("lifecycle/$id") },
                onSettings = { nav.navigate("serversettings/$id") },
                onBack = { nav.popBackStack() },
            )
        }
        composable("serversettings/{id}") { entry ->
            val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            ServerSettingsScreen(
                serverId = id,
                onBack = { nav.popBackStack() },
                onReturnToList = {
                    nav.popBackStack("servers", inclusive = false)
                },
            )
        }
        composable("lifecycle/{id}") { entry ->
            val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            LifecycleScreen(serverId = id, onBack = { nav.popBackStack() })
        }
        composable("monitor/{id}") { entry ->
            val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            LiveMonitorScreen(serverId = id, onBack = { nav.popBackStack() })
        }
        composable("agent/{id}") { entry ->
            val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            AgentScreen(serverId = id, onBack = { nav.popBackStack() })
        }
    }
}
