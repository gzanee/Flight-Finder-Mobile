package it.gzanee.flightfinder

import it.gzanee.flightfinder.data.google.GoogleHttp
import it.gzanee.flightfinder.data.google.HttpPage
import it.gzanee.flightfinder.domain.model.SearchJob
import it.gzanee.flightfinder.domain.repository.SearchStore
import it.gzanee.flightfinder.domain.search.EngineClock
import java.net.URLDecoder

/** Un Google che risponde con le pagine date, per tfs; le richieste fatte restano in [asked]. */
class FakeGoogle(private val handler: (url: String, headers: Map<String, String>, n: Int) -> HttpPage) : GoogleHttp {
    val asked = ArrayList<Pair<String, Map<String, String>>>()
    val posts = ArrayList<Map<String, String>>()

    override suspend fun get(url: String, headers: Map<String, String>): HttpPage {
        asked += url to headers
        return handler(url, headers, asked.size)
    }

    override suspend fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String>): HttpPage {
        posts += fields
        return HttpPage(200, "https://www.google.com/", "", listOf("SOCS=fresh-socs; Domain=.google.com, NID=1; Path=/"))
    }

    companion object {
        fun tfsOf(url: String): String = URLDecoder.decode(Regex("[?&]tfs=([^&]+)").find(url)!!.groupValues[1], "UTF-8")

        /** Risponde solo alle richieste che ha fatto il portale, con le sue pagine. */
        fun serving(pages: Map<String, String>) = FakeGoogle { url, _, _ ->
            HttpPage(200, url, pages[tfsOf(url)] ?: throw AssertionError("Richiesta non fatta dal portale: $url"))
        }
    }
}

/** Orologio finto: ogni lettura avanza di un millisecondo, le pause non aspettano. */
class FakeClock(var t: Double = System.currentTimeMillis() / 1000.0) : EngineClock {
    val sleeps = ArrayList<Double>()
    override fun now(): Double {
        t += 0.001
        return t
    }
    override suspend fun sleep(seconds: Double) {
        sleeps += seconds
        t += seconds
    }
}

class InMemorySearchStore : SearchStore {
    val jobs = LinkedHashMap<String, SearchJob>()
    override suspend fun load(id: String) = jobs[id]
    override suspend fun save(job: SearchJob) {
        jobs[job.id] = job
    }
    override suspend fun list() = jobs.values.toList()
    override suspend fun remove(id: String) {
        jobs.remove(id)
    }
}
