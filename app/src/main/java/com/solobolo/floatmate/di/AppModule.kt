package com.solobolo.floatmate.di

import android.content.Context
import com.solobolo.floatmate.service.FloatMateSharedPrefs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFloatMateSharedPrefs(
        @ApplicationContext context: Context
    ): FloatMateSharedPrefs = FloatMateSharedPrefs(context)
}