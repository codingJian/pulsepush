package com.shing.pulsepush.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shing.pulsepush.ui.auth.AuthScreen
import com.shing.pulsepush.ui.channels.ChannelsScreen
import com.shing.pulsepush.ui.detail.MessageDetailScreen
import com.shing.pulsepush.ui.inbox.InboxScreen
import com.shing.pulsepush.ui.settings.SettingsScreen

object Routes {
    const val LOGIN = "login"
    const val INBOX = "inbox"
    const val CHANNELS = "channels"
    const val SETTINGS = "settings"
    const val ARG_MSG_ID = "msgId"
    const val DETAIL = "detail/{$ARG_MSG_ID}"

    fun detail(msgId: String): String = "detail/$msgId"
    val mainTabs = listOf(INBOX, CHANNELS, SETTINGS)
}

@Composable
fun PulsePushNavHost(
    isLoggedIn: Boolean?,
    pendingMsgId: String?,
    onPendingMsgHandled: () -> Unit
) {
    val navController = rememberNavController()

    // Auth gate: login state comes from DataStore (null = still loading).
    LaunchedEffect(isLoggedIn) {
        when (isLoggedIn) {
            true -> navController.navigate(Routes.INBOX) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
            false -> navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
            null -> Unit
        }
    }

    // Notification tap: once logged in, jump straight to the message detail.
    LaunchedEffect(pendingMsgId, isLoggedIn) {
        if (pendingMsgId != null && isLoggedIn == true) {
            navController.navigate(Routes.detail(pendingMsgId)) {
                launchSingleTop = true
            }
            onPendingMsgHandled()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in Routes.mainTabs

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomBar(navController = navController)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LOGIN,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.LOGIN) { AuthScreen() }
            composable(Routes.INBOX) {
                InboxScreen(onMessageClick = { msgId ->
                    navController.navigate(Routes.detail(msgId))
                })
            }
            composable(Routes.CHANNELS) { ChannelsScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument(Routes.ARG_MSG_ID) { type = NavType.StringType })
            ) {
                MessageDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    NavigationBar {
        val items = listOf(
            Triple(Routes.INBOX, "消息", Icons.Filled.Email),
            Triple(Routes.CHANNELS, "通道", Icons.Filled.List),
            Triple(Routes.SETTINGS, "设置", Icons.Filled.Settings)
        )
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        items.forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = currentDestination?.hierarchy?.any { it.route == route } == true,
                onClick = {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) }
            )
        }
    }
}
