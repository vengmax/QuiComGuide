package com.wllcom.quicomguide

import android.app.Application
import com.wllcom.quicomguide.data.ml.EmbeddingProvider
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MyApp : Application() {

    @Inject
    lateinit var embeddingProvider: EmbeddingProvider

    override fun onCreate() {
        super.onCreate()

        CoroutineScope(Dispatchers.Default).launch {
            embeddingProvider.warmUp()
        }
    }
}