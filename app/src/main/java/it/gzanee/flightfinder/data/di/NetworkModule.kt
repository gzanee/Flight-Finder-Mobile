package it.gzanee.flightfinder.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.gzanee.flightfinder.data.google.GoogleHttp
import it.gzanee.flightfinder.data.google.OkHttpGoogleHttp
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Provides
    @Singleton
    fun provideGoogleHttp(client: OkHttpClient): GoogleHttp = OkHttpGoogleHttp(client)
}
