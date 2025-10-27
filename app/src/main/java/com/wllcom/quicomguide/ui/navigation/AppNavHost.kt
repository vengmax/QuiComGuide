package com.wllcom.quicomguide.ui.navigation

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.wllcom.quicomguide.data.source.cloud.AuthService
import com.wllcom.quicomguide.ui.screens.*
import com.wllcom.quicomguide.ui.viewmodel.AuthViewModel
import com.wllcom.quicomguide.ui.viewmodel.MaterialsViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AppNavHost(
    navController: NavHostController,
    viewModel: MaterialsViewModel,
    systemPadding: PaddingValues
) {
    val context = LocalContext.current
    val viewModelAuth: AuthViewModel = hiltViewModel()
    val authState by viewModelAuth.authState.collectAsState()

    // init WebView
    val sharedWebView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
        }
    }

    // loading effect WebView
    DisposableEffect(Unit) {
        val webClient = object : WebViewClient() {
            override fun onPageCommitVisible(view: WebView?, url: String?) {
                view?.visibility = View.VISIBLE
                view?.animate()?.alpha(1f)?.setDuration(500)?.start()
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                view?.visibility = View.INVISIBLE
                view?.alpha = 0f
            }
        }

        sharedWebView.webViewClient = webClient

        onDispose {
            try {
                (sharedWebView.parent as? ViewGroup)?.removeView(sharedWebView)
                sharedWebView.stopLoading()
                sharedWebView.loadUrl("about:blank")
                sharedWebView.removeAllViews()
                sharedWebView.destroy()
            } catch (t: Throwable) { }
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("signIn") {
            SignInScreen(navController)
        }
        composable("home") {
            HomeScreen(
                systemPadding = systemPadding,
                navController = navController,
                viewModel = viewModel,
                sharedWebView = sharedWebView
            )
        }
        composable("tests") {
            TestsScreen(systemPadding = systemPadding)
        }
        composable("library") {
            LibraryScreen(
                systemPadding = systemPadding,
                navController = navController
            )
        }
        composable("profile") {
            ProfileScreen(
                systemPadding = systemPadding,
                navController = navController
            )
        }

        composable("group/{groupId}") { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId")
            GroupScreen(
                systemPadding = systemPadding,
                navController = navController,
                groupId = groupId,
            )
        }
        composable("material/{materialId}") { backStackEntry ->
            val materialId = backStackEntry.arguments?.getString("materialId")
            MaterialDetailScreen(
                systemPadding = systemPadding,
                materialId = materialId,
                navController = navController,
                sharedWebView = sharedWebView
            )
        }

        composable("addMaterial") {
            AddEditMaterialScreen(
                systemPadding = systemPadding,
                navController = navController,
                viewModel = viewModel,
                materialId = null,
            )
        }
        composable("editMaterial/{materialId}") { backStackEntry ->
            val materialId = backStackEntry.arguments?.getString("materialId")
            AddEditMaterialScreen(
                systemPadding = systemPadding,
                navController = navController,
                viewModel = viewModel,
                materialId = materialId,
            )
        }
    }

    LaunchedEffect(authState) {
        if (authState != null && authState !is AuthService.AuthState.Authenticated) {
            navController.navigate("signIn")
        }
    }
}