package network.selfevolving.parastoo.mobile.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import network.selfevolving.parastoo.mobile.ui.screens.AgentScreen
import network.selfevolving.parastoo.mobile.ui.screens.DashboardScreen
import network.selfevolving.parastoo.mobile.ui.screens.LifecycleScreen
import network.selfevolving.parastoo.mobile.ui.screens.LiveMonitorScreen
import network.selfevolving.parastoo.mobile.ui.screens.ProvisionScreen
import network.selfevolving.parastoo.mobile.ui.screens.ServersScreen

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "servers") {
        composable("servers") {
            ServersScreen(
                onOpen = { id -> nav.navigate("dashboard/$id") },
                onAddProvision = { nav.navigate("provision") },
            )
        }
        composable("provision") {
            ProvisionScreen(onDone = { nav.popBackStack() })
        }
        composable("dashboard/{id}") { entry ->
            val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            DashboardScreen(
                serverId = id,
                onMonitor = { nav.navigate("monitor/$id") },
                onAgent = { nav.navigate("agent/$id") },
                onLifecycle = { nav.navigate("lifecycle/$id") },
                onBack = { nav.popBackStack() },
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
