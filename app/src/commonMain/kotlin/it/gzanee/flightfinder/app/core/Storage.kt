package it.gzanee.flightfinder.app.core

import it.gzanee.flightfinder.engine.CookieStore
import it.gzanee.flightfinder.engine.JobStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.Path

/** Saved searches: one JSON file each, replaced atomically so a crash never leaves half a file. */
class FileJobStore(private val dir: Path, private val fs: FileSystem = Platform.fileSystem) : JobStore {
    private fun file(id: String) = dir / "$id.json"

    override suspend fun load(id: String): String? = withContext(Dispatchers.IO) {
        if (fs.exists(file(id))) fs.read(file(id)) { readUtf8() } else null
    }

    override suspend fun save(id: String, body: String) = withContext(Dispatchers.IO) {
        fs.createDirectories(dir)
        val tmp = dir / "$id.json.tmp"
        fs.write(tmp) { writeUtf8(body) }
        fs.atomicMove(tmp, file(id))
    }

    override suspend fun list(): List<String> = withContext(Dispatchers.IO) {
        if (!fs.exists(dir)) emptyList() else fs.list(dir).map { it.name }.filter { it.endsWith(".json") }.map { it.removeSuffix(".json") }
    }

    override suspend fun remove(id: String) = withContext(Dispatchers.IO) { fs.delete(file(id), mustExist = false) }
}

/** Google's consent cookie, renewed weekly like the Python engine. */
class FileCookieStore(private val file: Path, private val fs: FileSystem = Platform.fileSystem) : CookieStore {
    override suspend fun load(): Map<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            if (!fs.exists(file)) return@runCatching null
            val blob = Json.parseToJsonElement(fs.read(file) { readUtf8() }).jsonObject
            val ts = blob["ts"]?.jsonPrimitive?.double ?: 0.0
            if (it.gzanee.flightfinder.engine.Calendar.nowSeconds() - ts > 7 * 24 * 3600) null
            else blob["cookies"]?.jsonObject?.mapValues { e -> e.value.jsonPrimitive.content }
        }.getOrNull()
    }

    override suspend fun save(cookies: Map<String, String>) = withContext(Dispatchers.IO) {
        file.parent?.let(fs::createDirectories)
        val blob = JsonObject(mapOf("ts" to JsonPrimitive(it.gzanee.flightfinder.engine.Calendar.nowSeconds()), "cookies" to JsonObject(cookies.mapValues { JsonPrimitive(it.value) })))
        fs.write(file) { writeUtf8(blob.toString()) }
        Unit
    }
}
