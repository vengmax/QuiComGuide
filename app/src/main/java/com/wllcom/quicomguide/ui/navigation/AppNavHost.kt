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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.wllcom.quicomguide.ui.screens.*
import com.wllcom.quicomguide.ui.viewmodel.MaterialsViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AppNavHost(
    navController: NavHostController,
    viewModel: MaterialsViewModel,
    paddingValues: PaddingValues
) {
    // init WebView
    val context = LocalContext.current
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
        composable("home") {
            HomeScreen(
                navController = navController,
                viewModel = viewModel,
                contentPadding = paddingValues,
                sharedWebView = sharedWebView
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
                contentPadding = paddingValues,
                sharedWebView = sharedWebView
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