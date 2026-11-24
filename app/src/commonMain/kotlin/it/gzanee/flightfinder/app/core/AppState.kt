package it.gzanee.flightfinder.app.core

import it.gzanee.flightfinder.app.resources.Res
import it.gzanee.flightfinder.engine.Airports
import it.gzanee.flightfinder.engine.Http
import it.gzanee.flightfinder.engine.JobManager
import it.gzanee.flightfinder.engine.LiveQuotes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** The engine on this phone and the search form, shared by every screen. */
object AppState {
    lateinit var live: LiveQuotes
        private set
    lateinit var jobs: JobManager
        private set

    /** Bumped after every saved step of a search, so screens refresh. */
    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick

    val form = MutableStateFlow(FormState.initial())

    private var started = false

    /** Load the airport catalog and create the engine (once). `http` replaces the network in demos. */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun start(http: Http? = null) {
        if (started) return
        withContext(Dispatchers.Default) {
            suspend fun file(name: String) = Res.readBytes("files/$name").decodeToString()
            Airports.install(file("airports.json"), file("places.json"), file("routes.json"), file("airlines.json"), file("countries_it.json"))
        }
        val dir = Platform.dataDir()
        live = LiveQuotes(http ?: KtorHttp(Platform.httpClient()), FileCookieStore(dir / "settings" / "google_consent.json"))
        jobs = JobManager(FileJobStore(dir / "searches"), live)
        var awake = false
        jobs.subscribe { job ->
            _tick.value = _tick.value + 1
            val running = job.running
            if (running != awake) {
                awake = running
                Platform.keepScreenOn(running)
            }
        }
        started = true
    }
}
