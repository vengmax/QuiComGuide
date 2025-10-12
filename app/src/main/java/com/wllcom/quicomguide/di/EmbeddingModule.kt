package com.wllcom.quicomguide.di

import android.content.Context
import com.wllcom.quicomguide.data.ml.EmbeddingProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EmbeddingModule {

    @Provides
    @Singleton
    fun provideEmbeddingProvider(@ApplicationContext ctx: Context): EmbeddingProvider {
        return EmbeddingProvider(
            ctx = ctx,
            tfliteName = "intfloat_multilingual-e5-small.tflite",
            tokenizerName = "tokenizer.json",
            maxLen = 512
        )
    }
}