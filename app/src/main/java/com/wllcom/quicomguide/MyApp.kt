package com.wllcom.quicomguide

import android.app.Application
import android.content.Context
import com.wllcom.quicomguide.data.ml.EmbeddingProvider
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.lingala.zip4j.ZipFile
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

fun unzipAssetToFilesDir(context: Context, assetName: String, outputDir: File) {
    val tempZipFile = File(context.cacheDir, assetName)
    context.assets.open(assetName).use { input ->
        FileOutputStream(tempZipFile).use { output ->
            input.copyTo(output)
        }
    }

    val zipFile = ZipFile(tempZipFile)
    zipFile.extractAll(outputDir.absolutePath)

    tempZipFile.delete()
}

@HiltAndroidApp
class MyApp : Application() {

    @Inject
    lateinit var embeddingProvider: EmbeddingProvider

    override fun onCreate() {
        super.onCreate()

        CoroutineScope(Dispatchers.Default).launch {

            val outputDir = File(filesDir, "model")
            if (!outputDir.exists()) outputDir.mkdirs()
            val modelFile = File(outputDir, "intfloat_multilingual-e5-small.tflite")
            val tokenizerFile = File(outputDir, "tokenizer.json")

            if (!modelFile.exists() || !tokenizerFile.exists()) {
                unzipAssetToFilesDir(this@MyApp, "model.zip", outputDir)
            }

            embeddingProvider.setPaths(modelFile.absolutePath, tokenizerFile.absolutePath)
            embeddingProvider.warmUp()
        }
    }
}