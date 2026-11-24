package it.gzanee.flightfinder.app

import it.gzanee.flightfinder.engine.Http
import it.gzanee.flightfinder.engine.HttpResponse
import it.gzanee.flightfinder.engine.base64Decode
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLDecoder

/**
 * A deterministic demo Google for the desktop preview and UI tests: it reads the
 * airports and dates of each request and invents plausible flights. Never in the apps.
 */
class DemoHttp(private val latencyMs: Long = 60) : Http {
    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
        delay(latencyMs)
        val tfs = Regex("[?&]tfs=([^&]+)").find(url)?.groupValues?.get(1)?.let { URLDecoder.decode(it, "UTF-8") } ?: return HttpResponse(200, url, "")
        return HttpResponse(200, url, page(tfs))
    }

    override suspend fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String>) = HttpResponse(200, url, "")

    private class Leg(var date: String = "", val from: MutableList<String> = ArrayList(), val to: MutableList<String> = ArrayList())

    private fun fields(b: List<Int>): List<Pair<Int, Any>> {
        val out = ArrayList<Pair<Int, Any>>()
        var i = 0
        fun varint(): Long { var n = 0L; var s = 0; while (true) { val x = b[i++]; n = n or ((x and 0x7F).toLong() shl s); if (x and 0x80 == 0) return n; s += 7 } }
        while (i < b.size) {
            val tag = varint().toInt()
            if (tag and 7 == 0) out += (tag shr 3) to varint() else { val len = varint().toInt(); out += (tag shr 3) to b.subList(i, i + len); i += len }
        }
        return out
    }

    @Suppress("UNCHECKED_CAST")
    private fun page(tfs: String): String {
        var trip = 2L
        val legs = ArrayList<Leg>()
        for ((f, v) in fields(base64Decode(tfs))) {
            if (f == 19) trip = v as Long
            if (f != 3) continue
            val leg = Leg()
            for ((g, w) in fields(v as List<Int>)) {
                if (g == 2) leg.date = (w as List<Int>).map { it.toChar() }.joinToString("")
                if (g == 13 || g == 14) {
                    val code = fields(w as List<Int>).first { it.first == 2 }.second as List<Int>
                    (if (g == 13) leg.from else leg.to) += code.map { it.toChar() }.joinToString("")
                }
            }
            legs += leg
        }
        val leg = legs.first()
        val (y, m, d) = leg.date.split("-").map(String::toInt)
        val airlines = listOf("FR" to "Ryanair", "U2" to "easyJet", "W6" to "Wizz Air", "VY" to "Vueling", "AZ" to "ITA Airways")
        val rows = ArrayList<JsonElement>()
        fun n(x: Number) = JsonPrimitive(x)
        for (from in leg.from) for (to in leg.to) {
            if (from == to) continue
            val rnd = java.util.Random("$tfs|$from|$to".hashCode().toLong())
            repeat(rnd.nextInt(4)) {
                val (code, name) = airlines[rnd.nextInt(airlines.size)]
                val dep = 6 + rnd.nextInt(16)
                val minute = listOf(0, 10, 25, 40, 55)[rnd.nextInt(5)]
                val dur = 70 + rnd.nextInt(120)
                val arr = dep * 60 + minute + dur
                val next = java.time.LocalDate.of(y, m, d).plusDays((arr / 1440).toLong())
                val fl = MutableList<JsonElement>(33) { JsonNull }
                fl[3] = JsonPrimitive(from); fl[6] = JsonPrimitive(to)
                fl[8] = JsonArray(listOf(n(dep), n(minute))); fl[10] = JsonArray(listOf(n(arr / 60 % 24), n(arr % 60))); fl[11] = n(dur)
                fl[20] = JsonArray(listOf(n(y), n(m), n(d))); fl[21] = JsonArray(listOf(n(next.year), n(next.monthValue), n(next.dayOfMonth)))
                fl[22] = JsonArray(listOf(JsonPrimitive(code), JsonPrimitive((1000 + rnd.nextInt(8000)).toString())))
                val detail = MutableList<JsonElement>(25) { JsonNull }
                detail[0] = JsonPrimitive(code); detail[1] = JsonArray(listOf(JsonPrimitive(name))); detail[2] = JsonArray(listOf(JsonArray(fl))); detail[9] = n(dur)
                val price = ((19 + rnd.nextInt(140)) * (if (trip == 1L) 1.7 else 1.0)).toInt()
                rows.add(JsonArray(listOf(JsonArray(detail), JsonArray(listOf(JsonArray(listOf(JsonNull, n(price))), JsonPrimitive("GgUaA0VVUg=="))), JsonNull, JsonNull, JsonNull, JsonNull, JsonNull, JsonNull, JsonPrimitive("demo"))))
            }
        }
        val payload = MutableList<JsonElement>(24) { JsonNull }
        if (rows.isNotEmpty()) {
            payload[2] = JsonArray(listOf(JsonArray(rows)))
        } else {
            payload[0] = JsonArray(listOf(JsonNull, JsonArray(listOf(JsonArray(listOf(n(1))))), n(0), JsonPrimitive("r"), JsonPrimitive("s")))
            payload[1] = JsonArray(listOf(JsonArray(listOf(
                JsonArray(leg.from.map { JsonArray(listOf(JsonArray(listOf(JsonPrimitive(it), n(0))), JsonPrimitive("o"))) }),
                JsonArray(leg.to.map { JsonArray(listOf(JsonArray(listOf(JsonPrimitive(it), n(0))), JsonPrimitive("d"))) }),
            ))))
            payload[7] = JsonArray(listOf(JsonArray(emptyList()), JsonArray(listOf(JsonArray(emptyList()), JsonArray(emptyList())))))
            payload[12] = JsonArray(listOf(JsonPrimitive("ctx"), n(0)))
            payload[23] = n(1)
        }
        return "<script class=\"ds:1\">AF_initDataCallback({data:${JsonArray(payload)},sideChannel:{}});</script>"
    }
}
