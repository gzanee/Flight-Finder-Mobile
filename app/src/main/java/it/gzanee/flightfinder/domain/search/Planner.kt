package it.gzanee.flightfinder.domain.search

import it.gzanee.flightfinder.domain.model.AirportChoice
import it.gzanee.flightfinder.domain.model.DateMode
import it.gzanee.flightfinder.domain.model.DatePair
import it.gzanee.flightfinder.domain.model.Plan
import it.gzanee.flightfinder.domain.model.REAL_ROUTES
import it.gzanee.flightfinder.domain.model.SearchRequest
import it.gzanee.flightfinder.domain.model.SearchValidationError
import it.gzanee.flightfinder.domain.model.TripType
import java.time.LocalDate

/*
 * Pianificazione del calendario: port di flexible.py. Le liste di rotte sono
 * candidati, mai la prova che un volo esista in una data.
 */

/** Google accetta fino a 10 aeroporti per lato; con gli scali cura la lista, quindi una rotta alla volta. */
const val MAX_AIRPORTS_PER_SEARCH = 10
/** Google vende i voli fino a 330 giorni da oggi. */
const val GOOGLE_HORIZON_DAYS = 330L
private const val MAX_TASKS = 50_000

/** (da, a) */
typealias Route = Pair<String, String>

/** Una richiesta a Google: aeroporti di partenza e di arrivo. */
data class Batch(val from: List<String>, val to: List<String>)

fun SearchRequest.datePairs(): List<DatePair> {
    if (dateMode == DateMode.FIXED) return listOf(DatePair(startDate, if (isRoundTrip) endDate else null))
    return generateSequence(startDate) { it.plusDays(1) }.takeWhile { it <= endDate }
        .filter { it.dayOfWeek in departureDays }
        .flatMap { day ->
            if (tripType == TripType.ONE_WAY) {
                sequenceOf(DatePair(day, null))
            } else {
                (minNights..maxNights).asSequence().map { day.plusDays(it.toLong()) }
                    .filter { it <= endDate && it.dayOfWeek in returnDays }
                    .map { DatePair(day, it) }
            }
        }.toList()
}

/** Tratte di ritorno di un'andata origine → destinazione. */
fun SearchRequest.returnRoutes(origin: String, destination: String): List<Route> {
    if (tripType == TripType.ONE_WAY) return emptyList()
    val departures = if (returnDeparture == AirportChoice.GROUP) destinations else listOf(destination)
    val homes = if (returnAirport == AirportChoice.GROUP) origins else listOf(origin)
    return departures.flatMap { away -> homes.filter { it != away }.map { away to it } }
}

val SearchRequest.airportsPerSearch get() = if (maxStops == 0) MAX_AIRPORTS_PER_SEARCH else 1

/**
 * Copre esattamente le coppie (da, a) con richieste fino a `size` aeroporti per lato,
 * raggruppando gli aeroporti che hanno le stesse controparti.
 */
fun pairBatches(input: List<Route>, size: Int): List<Batch> {
    val pairs = input.distinct()
    if (size <= 1) return pairs.map { (a, b) -> Batch(listOf(a), listOf(b)) }
    fun grouped(swap: Boolean): List<Batch> {
        val partners = LinkedHashMap<String, MutableList<String>>()
        for ((a, b) in pairs) {
            val (key, other) = if (swap) b to a else a to b
            partners.getOrPut(key) { ArrayList() } += other
        }
        val groups = LinkedHashMap<List<String>, MutableList<String>>()
        for ((key, others) in partners) groups.getOrPut(others) { ArrayList() } += key
        return groups.flatMap { (others, keys) ->
            keys.chunked(size).flatMap { k -> others.chunked(size).map { o -> if (swap) Batch(o, k) else Batch(k, o) } }
        }
    }
    val plain = grouped(swap = false)
    val swapped = grouped(swap = true)
    return if (swapped.size < plain.size) swapped else plain
}

/**
 * Il lavoro giorno per giorno di una richiesta: ogni tratta si cerca una volta.
 * Ogni giorno prima le andate, poi i ritorni che le andate trovate rendono utili.
 */
class Schedule(val request: SearchRequest, destinations: List<String>, today: LocalDate) {
    val size = request.airportsPerSearch
    val horizon: LocalDate = today.plusDays(GOOGLE_HORIZON_DAYS)
    val datePairs: List<DatePair>
    val unsoldFrom: LocalDate?
    val days: List<LocalDate>
    val outDays: Set<LocalDate>
    val backDays: List<LocalDate>
    val outsFor = LinkedHashMap<LocalDate, MutableList<LocalDate>>()
    val lastBack = HashMap<LocalDate, LocalDate?>()
    val routes: List<Route> = destinations.flatMap { d -> request.origins.filter { it != d }.map { it to d } }
    val outBatches = pairBatches(routes, size)
    val returns: List<Route> = routes.flatMap { (o, d) -> request.returnRoutes(o, d) }.distinct()
    val backBound = pairBatches(returns, size).size
    val total: Int

    init {
        val pairs = request.datePairs()
        datePairs = pairs.filter { (it.back ?: it.departure) <= horizon }
        unsoldFrom = (pairs - datePairs.toSet()).minOfOrNull { it.back ?: it.departure }
        val out = datePairs.map { it.departure }.distinct().sorted()
        backDays = datePairs.mapNotNull { it.back }.distinct().sorted()
        days = (out + backDays).distinct().sorted()
        outDays = out.toSet()
        for ((departure, back) in datePairs) {
            if (back != null) outsFor.getOrPut(back) { ArrayList() } += departure
            lastBack[departure] = listOfNotNull(lastBack[departure], back).maxOrNull()
        }
        total = out.size * outBatches.size + backDays.size * backBound
    }

    /** Richieste per i ritorni che servono, mai più di [backBound]. */
    fun returnBatches(wanted: Collection<Route>): List<Batch> {
        if (size == 1) return pairBatches(wanted.toList(), 1)
        val aways = wanted.map { it.first }.toSet()
        return pairBatches(returns.filter { it.first in aways }, size)
    }

    /** Richieste stimate che mancano; `returnShare` è la quota di ritorni davvero serviti finora. */
    fun remaining(day: Int, stage: Int, batch: Int, returnShare: Double): Double {
        var outs = 0
        var rets = 0
        for (i in day until days.size) {
            val current = i == day
            if (days[i] in outDays && !(current && stage != 0)) outs += outBatches.size - if (current) batch else 0
            if (days[i] in outsFor) rets += backBound
        }
        return outs + rets * returnShare
    }

    fun unitsBefore(dayIndex: Int): Int = days.take(dayIndex).sumOf { day ->
        (if (day in outDays) outBatches.size else 0) + (if (day in outsFor) backBound else 0)
    }
}

/**
 * Le passate di una ricerca. Con molti scali da cercare rotta per rotta, una prima
 * passata trova i voli diretti a gruppi, così i primi viaggi arrivano in pochi minuti.
 */
fun searchPasses(request: SearchRequest, destinations: List<String>, today: LocalDate): List<Schedule> {
    val full = Schedule(request, destinations, today)
    if (request.maxStops == 0 || full.routes.size <= MAX_AIRPORTS_PER_SEARCH) return listOf(full)
    return listOf(Schedule(request.copy(maxStops = 0), destinations, today), full)
}

private fun Int.withThousands() = "%,d".format(java.util.Locale.US, this)

/** Il lavoro e la copertura, mostrati prima di partire; non si tronca mai in silenzio. */
fun buildPlan(request: SearchRequest, routeMap: Map<String, List<String>>, catalog: AirportCatalog, today: LocalDate): Plan {
    val (allCandidates, coverage) = when {
        request.destinations.isNotEmpty() -> request.destinations to "Destinazioni indicate da te"
        request.destinationSet != REAL_ROUTES -> routeMap.values.flatten().distinct().sorted() to
            "Gruppo di destinazioni: ${catalog.destinationSets.getValue(request.destinationSet).label}"
        else -> routeMap.values.flatten().distinct().sorted() to "Destinazioni candidate dai cataloghi delle rotte; copertura non esaustiva"
    }
    val candidates = allCandidates.filter { c -> request.origins.any { it != c } }
    if (candidates.isEmpty()) throw SearchValidationError("Nessuna destinazione candidata disponibile.")
    val pairs = request.datePairs()
    val combinations = pairs.size * candidates.sumOf { c -> request.origins.count { it != c } }
    if (combinations > MAX_TASKS) {
        throw SearchValidationError(
            "La ricerca genera ${combinations.withThousands()} combinazioni: massimo ${MAX_TASKS.withThousands()}. " +
                "Riduci il periodo, le destinazioni o l'intervallo di notti.",
        )
    }
    val passes = searchPasses(request, candidates, today)
    val schedule = passes.last()
    var legs = 0
    for (pair in pairs) {
        for ((origin, destination) in schedule.routes) {
            legs += 1 + if (pair.back != null) request.returnRoutes(origin, destination).size else 0
            if (legs > MAX_TASKS * 2) throw SearchValidationError("Riduci gli aeroporti o le date: troppi controlli di ritorno.")
        }
    }
    val returns = schedule.routes.flatMap { (o, d) -> request.returnRoutes(o, d) }.toSet()
    return Plan(
        datePairs = pairs,
        destinations = candidates,
        candidateCount = candidates.size,
        combinations = combinations,
        distinctSearches = schedule.outDays.size * schedule.routes.size + schedule.backDays.size * returns.size,
        searchesUpperBound = passes.sumOf { it.total },
        airportsPerSearch = passes.first().size,
        directFirst = passes.size > 1,
        unsoldFrom = schedule.unsoldFrom,
        coverage = coverage,
        originRouteCounts = request.origins.associateWith { routeMap[it].orEmpty().size },
        notice = "Gli orari da catalogo non confermano un volo. Solo offerte live complete nei risultati.",
    )
}
