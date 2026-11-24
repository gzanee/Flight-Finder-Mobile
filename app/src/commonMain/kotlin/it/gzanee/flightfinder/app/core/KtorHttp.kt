package it.gzanee.flightfinder.app.core

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse as KtorResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.parameters
import it.gzanee.flightfinder.engine.Http
import it.gzanee.flightfinder.engine.HttpResponse

/** The same browser identity as the curl test that Google answered from a home connection. */
const val USER_AGENT =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"

/** The engine's HTTP over Ktor. Cookies are sent by hand: no cookie jar adds others. */
class KtorHttp(private val client: HttpClient) : Http {
    private suspend fun convert(r: KtorResponse) =
        HttpResponse(r.status.value, r.request.url.toString(), r.bodyAsText(), r.headers.getAll("Set-Cookie") ?: emptyList())

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse = convert(
        client.get(url) {
            header("User-Agent", USER_AGENT)
            headers.forEach { (k, v) -> header(k, v) }
        },
    )

    override suspend fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String>): HttpResponse = convert(
        client.submitForm(url, parameters { fields.forEach { (k, v) -> append(k, v) } }) {
            header("User-Agent", USER_AGENT)
            headers.forEach { (k, v) -> header(k, v) }
        },
    )
}
