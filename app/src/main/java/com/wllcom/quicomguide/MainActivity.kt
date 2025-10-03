package com.wllcom.quicomguide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wllcom.quicomguide.ui.components.BottomBar
import com.wllcom.quicomguide.ui.navigation.AppNavHost
import com.wllcom.quicomguide.ui.theme.QuiComGuideTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuiComGuideTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // показываем bottomBar только для корневых вкладок
                val showBottomBar = currentRoute in listOf("home", "tests", "library", "profile")

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) BottomBar(navController = navController)
                    }
                ) { padding ->
                    AppNavHost(navController = navController, paddingValues = padding)
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewMainActivity() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            BottomBar(navController = navController)
        }
    ) { padding ->
        AppNavHost(navController = navController, paddingValues = padding)
    }
}