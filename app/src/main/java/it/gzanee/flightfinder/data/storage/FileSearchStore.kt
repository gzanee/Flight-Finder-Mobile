package it.gzanee.flightfinder.data.storage

import it.gzanee.flightfinder.domain.model.FlightFinderJson
import it.gzanee.flightfinder.domain.model.SearchJob
import it.gzanee.flightfinder.domain.repository.SearchStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Una ricerca per file JSON; si scrive un file nuovo e lo si sostituisce, così un crash non lascia mezzo file. */
class FileSearchStore(private val dir: File) : SearchStore {
    private fun file(id: String) = File(dir, "$id.json")

    override suspend fun load(id: String): SearchJob? = withContext(Dispatchers.IO) {
        file(id).takeIf { it.exists() }?.let { FlightFinderJson.decodeFromString(SearchJob.serializer(), it.readText()) }
    }

    override suspend fun save(job: SearchJob) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val tmp = File(dir, "${job.id}.json.tmp")
        tmp.writeText(FlightFinderJson.encodeToString(SearchJob.serializer(), job))
        if (!tmp.renameTo(file(job.id))) {
            file(job.id).delete()
            tmp.renameTo(file(job.id))
        }
        Unit
    }

    override suspend fun list(): List<SearchJob> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
            // Un file danneggiato non blocca lo storico.
            runCatching { FlightFinderJson.decodeFromString(SearchJob.serializer(), f.readText()) }.getOrNull()
        }
    }

    override suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        file(id).delete()
        Unit
    }
}
