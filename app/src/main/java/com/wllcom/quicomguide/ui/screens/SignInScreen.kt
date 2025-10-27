package com.wllcom.quicomguide.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.wllcom.quicomguide.R
import com.wllcom.quicomguide.data.source.cloud.AuthService
import com.wllcom.quicomguide.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

//@Preview(showBackground = true)
//@Composable
//fun SignInScreenPreview() {
//    SignInApp {}
//}
//
//@Composable
//fun SignInApp(onEvent: (SignInEvent) -> Unit) {
////    MaterialTheme {
////        Surface(
////            modifier = Modifier.fillMaxSize(),
////            color = MaterialTheme.colorScheme.background
////        ) {
//            SignInScreen(
//                onGoogleClick = { onEvent(SignInEvent.Google) },
//                onContinueWithoutAccount = { onEvent(SignInEvent.ContinueWithoutAccount) },
//                onLearnMore = { onEvent(SignInEvent.LearnMore) }
//            )
////        }
////    }
//}

sealed class SignInEvent {
    object Google : SignInEvent()
    object ContinueWithoutAccount : SignInEvent()
    object LearnMore : SignInEvent()
}

@Composable
fun SignInScreen(
    navController: NavHostController
) {
    val viewModelAuth: AuthViewModel = hiltViewModel()
    val authState by viewModelAuth.authState.collectAsState()
    if(authState is AuthService.AuthState.Authenticated){
        navController.popBackStack()
    }

    val pendingIntent by viewModelAuth.pendingIntentEvent.collectAsState()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModelAuth.signIn()
        }
    }

    LaunchedEffect(pendingIntent) {
        pendingIntent?.let { p ->
            val request = IntentSenderRequest.Builder(p.intentSender).build()
            delay(500)
            launcher.launch(request)
        }
    }

    val backGroundColor = MaterialTheme.colorScheme.background

    // Верхний градиент или иллюстрация
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backGroundColor
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(Modifier.height(40.dp))

            // Header / логотип приложения
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(color = MaterialTheme.colorScheme.surface, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
//                Text("µ", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Удалить",
//                    tint = Color.White,
                    modifier = Modifier.size(100.dp)
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "Добро пожаловать",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Войдите, чтобы синхронизировать данные.\nВы всегда можете продолжить без аккаунта.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            // Карточка с подсказкой / фичей
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(color = MaterialTheme.colorScheme.onSurfaceVariant, shape = RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
//                        Text("★", fontSize = 28.sp)
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Удалить",
                            tint = Color(0xFFD4AF37),
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Text("Сохрани свои настройки", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Вход через Google позволяет восстановить профиль на любом устройстве.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // Google Sign-in button (custom-styled to match official look)
            GoogleSignInButton(
                text = "Войти через Google",
                onClick = { viewModelAuth.signIn() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            )

            Spacer(Modifier.height(12.dp))

            // Continue without account
            OutlinedButton(
                onClick = { navController.navigate("home") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Продолжить без аккаунта")
            }

            Spacer(Modifier.height(18.dp))

            // Дополнительная строка с мелкой ссылкой
            Text(
                modifier = Modifier
                    .clickable { }
                    .padding(6.dp),
                text = "Подробнее о входе и конфиденциальности",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

//            Spacer(Modifier.weight(1f))

            // Footer
//            Row(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(bottom = 12.dp),
//                horizontalArrangement = Arrangement.Center
//            ) {
//                Text(
//                    "Версия 1.0",
//                    style = MaterialTheme.typography.bodySmall,
//                    color = MaterialTheme.colorScheme.onSurfaceVariant
//                )
//            }
        }
    }
}

@Composable
fun GoogleSignInButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 2.dp,
        modifier = modifier
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(start = 12.dp, end = 36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Логотип Google слева
            Image(
                painter = painterResource(id = R.drawable.ic_google_logo),
                contentDescription = "Google",
                modifier = Modifier.size(24.dp)
            )

            Spacer(Modifier.width(0.dp))

            // Текст
            Text(
                color = Color.Black,
                text = text,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
//                fontWeight = FontWeight.Medium
            )
        }
    }
}

