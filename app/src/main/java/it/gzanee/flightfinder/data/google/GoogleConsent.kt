package it.gzanee.flightfinder.data.google

/** Dove si conserva il cookie di consenso (DataStore nell'app, memoria nei test). */
interface ConsentStore {
    suspend fun load(): Map<String, String>?
    suspend fun save(cookies: Map<String, String>)
}

class InMemoryConsentStore : ConsentStore {
    private var cookies: Map<String, String>? = null
    override suspend fun load() = cookies
    override suspend fun save(cookies: Map<String, String>) {
        this.cookies = cookies
    }
}

/**
 * Il consenso ai cookie di Google, come flightcore/consent.py: si manda il cookie
 * "Rifiuta tutto" (SOCS); se Google chiede di nuovo il consenso si rifà l'handshake.
 */
class GoogleConsent(private val http: GoogleHttp, private val store: ConsentStore) {
    private var cookies: Map<String, String>? = null

    suspend fun cookieHeader(): String {
        val current = cookies ?: (runCatching { store.load() }.getOrNull()?.takeIf { "SOCS" in it } ?: mapOf("SOCS" to DEFAULT_SOCS))
        cookies = current
        return current.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /** Handshake "Rifiuta tutto" (consent._perform_handshake); se non riesce resta il cookie predefinito. */
    suspend fun renew() {
        var fresh = mapOf("SOCS" to DEFAULT_SOCS)
        try {
            val probe = http.get(PROBE_URL, GoogleFlightsClient.BASE_HEADERS + ("Accept-Language" to "en-US,en;q=0.9"))
            val anchor = probe.body.indexOf("set_eom\" value=\"true\"")
            if (isConsentPage(probe) && anchor >= 0) {
                val start = probe.body.lastIndexOf("<form", anchor).coerceAtLeast(0)
                val end = probe.body.indexOf("</form>", anchor).let { if (it < 0) probe.body.length else it }
                val saved = http.postForm(CONSENT_SAVE_URL, hiddenInputs(probe.body.substring(start, end)), mapOf("Accept-Language" to "en-US,en;q=0.9"))
                parseSetCookies(saved.setCookies).takeIf { "SOCS" in it }?.let { fresh = it }
            }
        } catch (e: Exception) {
            // Resta il cookie predefinito.
        }
        cookies = fresh
        runCatching { store.save(fresh) }
    }

    companion object {
        /** Il cookie SOCS di "Rifiuta tutto", come lo mandano molti client open source. */
        const val DEFAULT_SOCS = "CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg"
        private val RELEVANT = setOf("SOCS", "CONSENT", "__Secure-ENID")
        private const val PROBE_URL = "https://www.google.com/travel/flights?hl=en&gl=IT&curr=EUR"
        private const val CONSENT_SAVE_URL = "https://consent.google.com/save"

        fun isConsentPage(page: HttpPage) = "consent.google.com" in page.url || "consent.google.com/" in page.body.take(2000)

        fun hiddenInputs(form: String): Map<String, String> = Regex("""<input[^>]*type="hidden"[^>]*>""").findAll(form).mapNotNull { m ->
            val name = Regex("""name="([^"]+)"""").find(m.value)?.groupValues?.get(1) ?: return@mapNotNull null
            name to unescapeHtml(Regex("""value="([^"]*)"""").find(m.value)?.groupValues?.get(1).orEmpty())
        }.toMap(LinkedHashMap())

        private fun unescapeHtml(text: String) = text
            .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toInt(16).toChar().toString() }
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

        private fun parseSetCookies(lines: List<String>): Map<String, String> = lines
            .flatMap { it.split(Regex(""",(?=\s*[A-Za-z0-9_\-]+=)""")) }
            .mapNotNull { Regex("""^\s*([^=;\s]+)=([^;]*)""").find(it)?.destructured?.let { (k, v) -> k to v } }
            .filter { it.first in RELEVANT }
            .toMap()
    }
}
