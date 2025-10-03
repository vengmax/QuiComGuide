package com.wllcom.quicomguide.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wllcom.quicomguide.ui.navigation.bottomNavItems

@Composable
fun BottomBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        bottomNavItems.forEach { screen ->
            val selected = currentRoute == screen.route ||
                    (screen.route == "library" && currentRoute?.contains("material") ?: false)
            NavigationBarItem(
                icon = {
                    Icon(
                        painter = painterResource(id = screen.iconRes),
                        contentDescription = screen.title,
                        tint = Color.Unspecified
                    )
                },
                label = { Text(screen.title) },
                selected = currentRoute == screen.route,
                onClick = {
                    if (!selected) {
                        // обычная навигация на вкладку
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    } else {
                        // Повторный клик на уже выбранную вкладку: возвращаемся на корень этого раздела
                        // Попробуем "сбросить" стек до этой вкладки, чтобы пользователь оказался в "главном" представлении вкладки.
                        navController.navigate(screen.route) {
                            popUpTo(screen.route) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = screen.bgColor
                )
            )
        }
    }
}

@Preview
@Composable
fun PreviewBottomBar() {
    BottomBar(navController = rememberNavController())
}