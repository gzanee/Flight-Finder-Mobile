package it.gzanee.flightfinder.app.core

import io.ktor.client.HttpClient
import okio.Path

/** What each phone does its own way. */
expect object Platform {
    /** Folder for saved searches and settings, private to the app. */
    fun dataDir(): Path

    /** The device's file system. */
    val fileSystem: okio.FileSystem

    /** A Ktor client on the system's network stack, without a cookie jar. */
    fun httpClient(): HttpClient

    /** Keep the screen on while a search runs: iOS suspends apps in background. */
    fun keepScreenOn(on: Boolean)

    /** The system share sheet. */
    fun share(text: String)

    val name: String
}
