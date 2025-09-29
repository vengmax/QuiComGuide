package com.wllcom.quicomguide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.wllcom.quicomguide.ui.components.AnimatedSearchField
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
                var query by remember { mutableStateOf("") }
                Scaffold(
                    bottomBar = { BottomBar(navController = navController) }
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

}