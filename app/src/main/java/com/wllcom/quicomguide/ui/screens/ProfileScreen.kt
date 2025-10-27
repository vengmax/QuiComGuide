package com.wllcom.quicomguide.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.wllcom.quicomguide.ui.viewmodel.AuthViewModel
import coil.compose.AsyncImage
import com.wllcom.quicomguide.data.source.cloud.AuthService
import com.wllcom.quicomguide.ui.components.BottomBar
import com.wllcom.quicomguide.ui.viewmodel.StorageViewModel

enum class Screen { LOGIN, PROFILE, SETTINGS }

@Composable
fun ProfileScreen(
    systemPadding: PaddingValues,
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel()
) {

//    val pendingIntent by viewModel.pendingIntentEvent.collectAsState()
//    val launcher = rememberLauncherForActivityResult(
//        ActivityResultContracts.StartIntentSenderForResult()
//    ) { result ->
//        if (result.resultCode == Activity.RESULT_OK) {
//            viewModel.signIn()
//        }
//    }
//
//    LaunchedEffect(pendingIntent) {
//        pendingIntent?.let { p ->
//            val request = IntentSenderRequest.Builder(p.intentSender).build()
//            launcher.launch(request)
//        }
//    }

    val authState by viewModel.authState.collectAsState()
    var screen by remember(authState) {
        mutableStateOf(if (authState is AuthService.AuthState.Authenticated) Screen.PROFILE else Screen.LOGIN)
    }

    when (screen) {
        Screen.LOGIN -> LoginScreen(systemPadding = systemPadding, navController = navController) { navController.navigate("signIn") }
        Screen.PROFILE -> ProfileScreen(
            systemPadding = systemPadding,
            navController = navController,
            authState = authState as AuthService.AuthState.Authenticated,
            onSettingsClick = { screen = Screen.SETTINGS }
        )

        Screen.SETTINGS -> SettingsScreen(
            systemPadding = systemPadding,
            onLogoutClick = { viewModel.signOut() },
            onBackClick = { screen = Screen.PROFILE },
        )
    }
}

@Composable
fun LoginScreen(systemPadding: PaddingValues, navController: NavController, onSignInClick: () -> Unit) {
    Scaffold(
        modifier = Modifier.padding(top = systemPadding.calculateTopPadding()),
        bottomBar = { BottomBar(navController = navController) }
    ) { bottomBoxPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottomBoxPadding)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onSignInClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AccountCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sign in with Google")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    systemPadding: PaddingValues,
    navController: NavController,
    authState: AuthService.AuthState.Authenticated,
    onSettingsClick: () -> Unit,
) {
    val viewModelStorage: StorageViewModel = hiltViewModel()
    if(authState.accessToken != null)
        viewModelStorage.getUserInfo(authState.accessToken)
    val userInfo by viewModelStorage.statusUserInfo.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = { BottomBar(navController = navController) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = authState.profilePictureUri,
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(16.dp))
            Text("${authState.displayName}", style = MaterialTheme.typography.titleLarge)
            Text("${authState.email}", style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(32.dp))

            var usedMemory by rememberSaveable { mutableStateOf(userInfo?.usedMemory) }
            var maxMemory by rememberSaveable { mutableStateOf(userInfo?.maxMemory) }
            if(userInfo != null){
                usedMemory = userInfo?.usedMemory
                maxMemory = userInfo?.maxMemory
            }
            val progress = if(usedMemory != null && maxMemory != null) usedMemory!!.toFloat()/maxMemory!!.toFloat() else 100f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = ProgressIndicatorDefaults.linearColor,
                trackColor = ProgressIndicatorDefaults.linearTrackColor,
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
            )
            Spacer(Modifier.height(8.dp))
            Text("${"%.2f".format(usedMemory?.let{it/(1024f*1024f*1024f)})} GB / ${maxMemory?.let{it/(1024*1024*1024)}} GB")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    systemPadding: PaddingValues,
    onLogoutClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ListItem(
                headlineContent = { Text("Облако") },
                leadingContent = { Icon(Icons.Default.Cloud, null) }
            )
            ListItem(
                headlineContent = { Text("Уведомления") },
                leadingContent = { Icon(Icons.Default.Notifications, null) }
            )

            Spacer(Modifier.height(20.dp))

            ListItem(
                modifier = Modifier.clickable(onClick = onLogoutClick),
                headlineContent = { Text("Выйти из аккаунта", color = Color.Red) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color.Red) }
            )
        }
    }
}