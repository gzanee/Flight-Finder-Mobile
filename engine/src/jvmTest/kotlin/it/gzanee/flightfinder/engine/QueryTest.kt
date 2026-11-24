package it.gzanee.flightfinder.engine

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class QueryTest {
    @Test
    fun tfsIsIdenticalToPython() {
        Fixtures.installCatalog()
        val cases = Fixtures.json("queries.json").jsonArray
        assertEquals(989, cases.size)
        for ((i, c) in cases.withIndex()) {
            val o = c.jsonObject
            val raw = o.getValue("request").jsonObject
            val r = json.decodeFromJsonElement(FlexibleRequest.serializer(), JsonObject(raw + mapOf(
                "origins" to o.getValue("origins"), "start_date" to o.getValue("day"), "end_date" to o.getValue("day"),
            )))
            val origins = o.getValue("origins").jsonArray.map { it.jsonPrimitive.content }
            val dests = o.getValue("destinations").jsonArray.map { it.jsonPrimitive.content }
            val day = o.getValue("day").jsonPrimitive.content
            val q = if (o.getValue("trip").jsonPrimitive.content == "round-trip") {
                roundTripQuery(origins[0], dests[0], day, o.getValue("back").jsonPrimitive.content, r)
            } else {
                oneWayQuery(origins, dests, day, r)
            }
            assertEquals(o.getValue("tfs").jsonPrimitive.content, tfs(q), "case $i")
        }
    }
}
