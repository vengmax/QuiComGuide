package com.wllcom.quicomguide.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.wllcom.quicomguide.data.repository.StorageRepository
import com.wllcom.quicomguide.data.source.cloud.AuthService
import com.wllcom.quicomguide.ui.components.TopBar
import com.wllcom.quicomguide.ui.viewmodel.SettingsViewModel
import com.wllcom.quicomguide.ui.viewmodel.StorageViewModel

enum class Screen { PROFILE, SETTINGS }

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
//    var screen by remember(authState) {
//        mutableStateOf(if (authState is AuthService.AuthState.NotAuthenticated) Screen.PROFILE else Screen.LOGIN)
//    }
    var screen by remember{ mutableStateOf(Screen.PROFILE) }

    when (screen) {
//        Screen.LOGIN -> LoginScreen(systemPadding = systemPadding, navController = navController) { navController.navigate("signIn") }
        Screen.PROFILE -> AccountScreen(
            systemPadding = systemPadding,
            navController = navController,
            authState = authState,
            onSettingsClick = { screen = Screen.SETTINGS }
        )

        Screen.SETTINGS -> SettingsScreen(
            systemPadding = systemPadding,
            authState = authState,
            onLogoutClick = { viewModel.signOut(); screen = Screen.PROFILE },
            onBackClick = { screen = Screen.PROFILE },
        )
    }
}

//@Composable
//fun LoginScreen(systemPadding: PaddingValues, navController: NavController, onSignInClick: () -> Unit) {
//    Scaffold(
//        modifier = Modifier.padding(top = systemPadding.calculateTopPadding()),
////        bottomBar = { BottomBar(navController = navController) }
//    ) { bottomBoxPadding ->
//        Box(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(bottomBoxPadding)
//                .padding(32.dp),
//            contentAlignment = Alignment.Center
//        ) {
//            Button(
//                onClick = onSignInClick,
//                shape = RoundedCornerShape(12.dp)
//            ) {
//                Icon(Icons.Default.AccountCircle, contentDescription = null)
//                Spacer(Modifier.width(8.dp))
//                Text("Sign in with Google")
//            }
//        }
//    }
//}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    systemPadding: PaddingValues,
    navController: NavController,
    authState: AuthService.AuthState?,
    onSettingsClick: () -> Unit,
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val viewModelStorage: StorageViewModel = hiltViewModel()
    var authenticated by remember { mutableStateOf<AuthService.AuthState.Authenticated?>(null) }
    if(authState is AuthService.AuthState.Authenticated) {
        authenticated = authState
        if (authState.accessToken != null)
            viewModelStorage.getUserInfo(authState.accessToken)
    }
    val userInfo by viewModelStorage.statusUserInfo.collectAsState()


    val statusSync by viewModelStorage.statusSync.collectAsState()
    val syncPercentage by viewModelStorage.syncPercentage.collectAsState()
    var syncMode by rememberSaveable { mutableStateOf(false) }
    var syncModeSuccessful by rememberSaveable { mutableStateOf(false) }
    var selectedOption by rememberSaveable { mutableStateOf("Объединить") }
    val options = listOf("Локально", "Удаленно", "Объединить")
    if(syncMode){
        AlertDialog (
            onDismissRequest = { if(syncModeSuccessful) syncMode = false },
            confirmButton = {
                if(syncModeSuccessful) {
                    TextButton(onClick = { syncMode = false }) {
                        Text("ОК")
                    }
                }
            },
            dismissButton = {},
            title = { Text("Синхронизация") },
            text = {
                if(!syncModeSuccessful) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        if(syncPercentage == 0)
                            Text("Проверка...")
                        else {
                            settingsViewModel.setLastAccountSync(authenticated!!.email!!)
                            Text("Идет синхронизация...")
                        }
                    }
                }
                else{
                    Text("Данные успешно синхронизированы!")
                }
            }
        )
        if(statusSync != null){
            if(statusSync == StorageRepository.StatusSync.SUCCESSFUL){
                syncModeSuccessful = true
            }
            else if (statusSync == StorageRepository.StatusSync.CONFLICT){
                AlertDialog (
                    onDismissRequest = { syncMode = false },
                    confirmButton = {
                        TextButton(onClick = {
                            if(selectedOption == "Локально"){
                                viewModelStorage.sync(
                                    authenticated!!.accessToken!!,
                                    StorageRepository.SyncMode.FORCE_LOCAL
                                )
                            }
                            else if(selectedOption == "Удаленно"){
                                viewModelStorage.sync(
                                    authenticated!!.accessToken!!,
                                    StorageRepository.SyncMode.FORCE_REMOTE
                                )
                            }
                            else if(selectedOption == "Объединить"){
                                viewModelStorage.sync(
                                    authenticated!!.accessToken!!,
                                    StorageRepository.SyncMode.MERGE
                                )
                            }
                        }) {
                            Text("Подтвердить")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { syncMode = false }) {
                            Text("Отмена")
                        }
                    },
                    title = { Text("Синхронизация") },
                    text = {
                        LazyColumn {
                            item{Text("Кажется случился конфликт данных или данные пренадлежат другому аккуанту.")}
                            item{Spacer(Modifier.height(5.dp))}
                            item{Text("Варианты синхронизации:")}
                            item{Text("1) \"Локально\" - удалить все материалы на Удаленном диске и загрузить текущие с Телефона")}
                            item{Text("2) \"Удаленно\" - удалить все материалы на Телефоне и загрузить с Удаленного диска")}
                            item{Text("3) \"Объединить\" - объединить материалы, но если есть материалы с одинаковыми заголовками" +
                                    ", тогда выберется наиболее актуальный материал")}
                            item{Spacer(Modifier.height(5.dp))}
                            item{Text("Выберите вариант:")}
                            items(options){ text ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedOption = text }
                                ) {
                                    RadioButton(
                                        selected = (text == selectedOption),
                                        onClick = { selectedOption = text }
                                    )
                                    Text(text = text)
                                }
                            }
                        }
                    }
                )
            }
            else if (statusSync == StorageRepository.StatusSync.ERROR){
                AlertDialog (
                    onDismissRequest = {
                        syncMode = false
                    },
                    confirmButton = {
                        TextButton(onClick = { syncMode = false }) {
                            Text("ОК")
                        }
                    },
                    dismissButton = {},
                    title = { Text("Ошибка синхронизации") },
                    text = {
                        Text("Произошла ошибка синхронизации! Проверьте подключение к интернету и повторите попытку позже.")
                    }
                )
            }
        }
    }

    Scaffold { padding ->

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item { Spacer(Modifier.height(100.dp)) }
                item {
                    if(authenticated != null) {
                        AsyncImage(
                            model = authenticated!!.profilePictureUri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                        )
                    }
                    else{
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(authenticated?.displayName?:"Нет данных", style = MaterialTheme.typography.titleLarge)
                    Text(authenticated?.email?:"Нет данных", style = MaterialTheme.typography.bodyMedium)


                    Spacer(Modifier.height(32.dp))
                }
                item {
                    var usedMemory by rememberSaveable { mutableStateOf(userInfo?.usedMemory) }
                    var maxMemory by rememberSaveable { mutableStateOf(userInfo?.maxMemory) }
                    if (userInfo != null) {
                        usedMemory = userInfo?.usedMemory
                        maxMemory = userInfo?.maxMemory
                    }
                    val progress =
                        if (usedMemory != null && maxMemory != null) usedMemory!!.toFloat() / maxMemory!!.toFloat() else 100f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = ProgressIndicatorDefaults.linearColor,
                        trackColor = ProgressIndicatorDefaults.linearTrackColor,
                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${"%.2f".format(usedMemory?.let { it / (1024f * 1024f * 1024f) })} GB / ${maxMemory?.let { it / (1024 * 1024 * 1024) }} GB")
                }
                item { Spacer(Modifier.height(20.dp)) }
                item {
                    Button(
                        onClick = {
                            if (authenticated != null) {
                                syncModeSuccessful = false
                                selectedOption = "Объединить"
                                viewModelStorage.sync(authenticated!!.accessToken!!)
                                syncMode = true
                            } else
                                navController.navigate("signIn")
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(if(authenticated != null) Icons.Default.Sync else Icons.Default.AccountCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if(authenticated != null) "Синхронизировать данные" else "Авторизоваться")
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
        TopBar(
            title = "Профиль",
            customButtons = true,
            composeCustomButtons = { modifier ->
                Box(contentAlignment = Alignment.Center,modifier = modifier
                    .width(44.dp)
                    .clickable { onSettingsClick() }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    systemPadding: PaddingValues,
    authState: AuthService.AuthState?,
    onLogoutClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val autoSync by settingsViewModel.isAutoSync.collectAsState()
    val notification by settingsViewModel.isNotification.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(top = 52.dp)
        ) {
            Row (verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 10.dp)) {
                ListItem(
                    modifier = Modifier.weight(1f),
                    headlineContent = { Text("Автосинхронизация") },
                    leadingContent = { Icon(Icons.Default.Cloud, null) }
                )
                Switch(
                    checked = autoSync,
                    onCheckedChange = {
                        settingsViewModel.setAutoSync(it)
                    }
                )
            }

            Row (verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 10.dp)) {
                ListItem(
                    modifier = Modifier.weight(1f),
                    headlineContent = { Text("Уведомления") },
                    leadingContent = { Icon(Icons.Default.Notifications, null) }
                )
                Switch(
                    checked = notification,
                    onCheckedChange = {
                        settingsViewModel.setNotification(it)
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            if(authState is AuthService.AuthState.Authenticated) {
                ListItem(
                    modifier = Modifier.clickable(onClick = onLogoutClick),
                    headlineContent = { Text("Выйти из аккаунта", color = Color.Red) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color.Red) }
                )
            }
        }

        TopBar(
            title = "Настройки",
            back = true,
            onBack = onBackClick
        )
    }
}