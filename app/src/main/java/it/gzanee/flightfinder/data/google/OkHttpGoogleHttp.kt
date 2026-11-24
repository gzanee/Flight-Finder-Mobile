package it.gzanee.flightfinder.data.google

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * La rete del telefono con OkHttp. I cookie si mandano a mano (OkHttp non ha un
 * cookie jar per default) con l'identità di Safari su iPhone, la stessa del test
 * con curl a cui Google ha risposto da una connessione di casa.
 */
class OkHttpGoogleHttp(private val client: OkHttpClient) : GoogleHttp {

    override suspend fun get(url: String, headers: Map<String, String>): HttpPage =
        execute(Request.Builder().url(url).get(), headers)

    override suspend fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String>): HttpPage {
        val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        return execute(Request.Builder().url(url).post(body), headers)
    }

    private suspend fun execute(builder: Request.Builder, headers: Map<String, String>): HttpPage = withContext(Dispatchers.IO) {
        builder.header("User-Agent", USER_AGENT)
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { response ->
            HttpPage(response.code, response.request.url.toString(), response.body.string(), response.headers("Set-Cookie"))
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"
    }
}
