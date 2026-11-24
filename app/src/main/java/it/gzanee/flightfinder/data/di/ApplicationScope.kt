package it.gzanee.flightfinder.data.di

import javax.inject.Qualifier

/** Lo scope delle ricerche: vive quanto l'app, non quanto una schermata. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
