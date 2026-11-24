package it.gzanee.flightfinder.data

import it.gzanee.flightfinder.Fixtures
import it.gzanee.flightfinder.data.google.GoogleQuery
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class GoogleQueryTest {
    @Test
    fun tfsIdenticaAlPortale() {
        val cases = Fixtures.json("queries.json").jsonArray
        assertEquals(989, cases.size)
        for ((i, case) in cases.withIndex()) {
            val c = case.jsonObject
            val day = c.getValue("day")
            val request = Fixtures.request(JsonObject(c.getValue("request").jsonObject + mapOf("origins" to c.getValue("origins"), "start_date" to day, "end_date" to day)))!!
            val origins = c.getValue("origins").jsonArray.map { it.jsonPrimitive.content }
            val destinations = c.getValue("destinations").jsonArray.map { it.jsonPrimitive.content }
            val date = LocalDate.parse(day.jsonPrimitive.content)
            val query = if (c.getValue("trip").jsonPrimitive.content == "round-trip") {
                GoogleQuery.roundTrip(origins[0], destinations[0], date, LocalDate.parse(c.getValue("back").jsonPrimitive.content), request)
            } else {
                GoogleQuery.oneWay(origins, destinations, date, request)
            }
            assertEquals("caso $i", c.getValue("tfs").jsonPrimitive.content, query.tfs)
        }
    }
}
