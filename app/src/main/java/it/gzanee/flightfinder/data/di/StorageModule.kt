package it.gzanee.flightfinder.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.gzanee.flightfinder.data.google.ConsentStore
import it.gzanee.flightfinder.data.repository.ThemeManagerImpl
import it.gzanee.flightfinder.data.storage.DataStoreConsentStore
import it.gzanee.flightfinder.data.storage.FileSearchStore
import it.gzanee.flightfinder.domain.repository.SearchStore
import it.gzanee.flightfinder.domain.repository.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideSearchStore(@ApplicationContext context: Context): SearchStore = FileSearchStore(File(context.filesDir, "searches"))

    @Provides
    @Singleton
    fun provideConsentStore(@ApplicationContext context: Context): ConsentStore = DataStoreConsentStore(context)

    @Provides
    @Singleton
    fun provideThemeManager(@ApplicationContext context: Context): ThemeManager = ThemeManagerImpl(context)

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob())
}
