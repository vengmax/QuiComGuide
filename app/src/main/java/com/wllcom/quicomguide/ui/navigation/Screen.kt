package com.wllcom.quicomguide.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.wllcom.quicomguide.R

sealed class Screen(
    val route: String,
    val title: String,
    @DrawableRes val iconRes: Int,
    val bgColor: Color
) {
    object Home :
        Screen("home", "Главная", R.drawable.ic_search, Color(0xFFFFA726))

    //    object Tests : Screen("tests", "Тесты", R.drawable.ic_brain, Color(0xFFFDD835))
    object Library :
        Screen("library", "Материалы", R.drawable.ic_books, Color(0xFFEF5350))

    object Profile : Screen("profile", "Профиль", R.drawable.ic_boy, Color(0xFFFFFFFF))
}

val bottomNavItems = listOf(
//    Screen.Home, Screen.Tests, Screen.Library, Screen.Profile
    Screen.Home, Screen.Library, Screen.Profile
)