package com.wllcom.quicomguide.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.wllcom.quicomguide.ui.screens.*

@Composable
fun AppNavHost(navController: NavHostController, paddingValues: PaddingValues) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(navController = navController, contentPadding = paddingValues)
        }
        composable(Screen.Tests.route) {
            TestsScreen(contentPadding = paddingValues)
        }
        composable(Screen.Library.route) {
            LibraryScreen(navController = navController, contentPadding = paddingValues)
        }
        composable(Screen.Profile.route) {
            ProfileScreen(contentPadding = paddingValues)
        }
        composable("material/{materialId}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("materialId")
            MaterialDetailScreen(
                materialId = id,
                navController = navController,
                contentPadding = paddingValues
            )
        }
    }
}