package com.yourname.aggregator.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yourname.aggregator.AppContainer
import com.yourname.aggregator.core.model.VideoItem
import com.yourname.aggregator.ui.favorites.FavoritesScreen
import com.yourname.aggregator.ui.home.HomeScreen
import com.yourname.aggregator.ui.player.PlayerScreen
import com.yourname.aggregator.ui.search.SearchScreen
import com.yourname.aggregator.ui.settings.SettingsScreen

private enum class TopLevelRoute(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    SEARCH("search", "Search", Icons.Filled.Search, Icons.Outlined.Search),
    FAVORITES("favorites", "Favorites", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

private const val ROUTE_PLAYER = "player"

@Composable
fun AppNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Holds the currently selected video cleanly without URL-encoding route args
    var activeVideo by remember { mutableStateOf<VideoItem?>(null) }

    val openVideo: (VideoItem) -> Unit = { video ->
        activeVideo = video
        navController.navigate(ROUTE_PLAYER)
    }

    val showBottomBar = currentRoute != ROUTE_PLAYER

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelRoute.entries.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = destination.label
                                )
                            },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelRoute.HOME.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(TopLevelRoute.HOME.route) {
                HomeScreen(
                    container = container,
                    onVideoClick = openVideo
                )
            }

            composable(TopLevelRoute.SEARCH.route) {
                SearchScreen(
                    container = container,
                    onVideoClick = openVideo
                )
            }

            composable(TopLevelRoute.FAVORITES.route) {
                FavoritesScreen(
                    container = container,
                    onVideoClick = openVideo
                )
            }

            composable(TopLevelRoute.SETTINGS.route) {
                SettingsScreen(
                    container = container
                )
            }

            composable(ROUTE_PLAYER) {
                val video = activeVideo
                if (video != null) {
                    PlayerScreen(
                        video = video,
                        container = container,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
