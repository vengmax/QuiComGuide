package com.wllcom.quicomguide.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.wllcom.quicomguide.ui.screens.*
import com.wllcom.quicomguide.ui.viewmodel.MaterialsViewModel

@Composable
fun AppNavHost(
    navController: NavHostController,
    viewModel: MaterialsViewModel,
    paddingValues: PaddingValues
) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                navController = navController,
                viewModel = viewModel,
                contentPadding = paddingValues
            )
        }
        composable("tests") {
            TestsScreen(contentPadding = paddingValues)
        }
        composable("library") {
            LibraryScreen(
                navController = navController,
                contentPadding = paddingValues
            )
        }
        composable("profile") {
            ProfileScreen(contentPadding = paddingValues)
        }
        composable("group/{groupId}") { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId")
            GroupScreen(
                navController = navController,
                groupId = groupId,
                contentPadding = paddingValues
            )
        }
        composable("material/{materialId}") { backStackEntry ->
            val materialId = backStackEntry.arguments?.getString("materialId")
            MaterialDetailScreen(
                materialId = materialId,
                navController = navController,
                contentPadding = paddingValues
            )
        }
        composable("addMaterial") {
            AddEditMaterialScreen(
                navController = navController,
                viewModel = viewModel,
                materialId = null,
                padding = paddingValues
            )
        }
        composable("editMaterial/{materialId}") { backStackEntry ->
            val materialId = backStackEntry.arguments?.getString("materialId")
            AddEditMaterialScreen(
                navController = navController,
                viewModel = viewModel,
                materialId = materialId,
                padding = paddingValues
            )
        }
    }
}