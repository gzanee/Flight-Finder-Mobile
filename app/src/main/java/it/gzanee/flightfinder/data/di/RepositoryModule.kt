package it.gzanee.flightfinder.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.gzanee.flightfinder.data.google.ConsentStore
import it.gzanee.flightfinder.data.google.GoogleHttp
import it.gzanee.flightfinder.data.repository.AirportRepositoryImpl
import it.gzanee.flightfinder.data.repository.SearchRepositoryImpl
import it.gzanee.flightfinder.domain.repository.AirportRepository
import it.gzanee.flightfinder.domain.repository.SearchRepository
import it.gzanee.flightfinder.domain.repository.SearchStore
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideAirportRepository(@ApplicationContext context: Context): AirportRepository = AirportRepositoryImpl(context)

    @Provides
    @Singleton
    fun provideSearchRepository(
        airports: AirportRepository,
        store: SearchStore,
        http: GoogleHttp,
        consentStore: ConsentStore,
        @ApplicationScope scope: CoroutineScope,
    ): SearchRepository = SearchRepositoryImpl(airports, store, http, consentStore, scope)
}
