package com.wllcom.quicomguide.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment

@Composable
fun TestsScreen(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Text("В разработке")
    }
}