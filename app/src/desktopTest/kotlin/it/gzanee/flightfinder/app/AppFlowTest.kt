package it.gzanee.flightfinder.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * The whole app on a demo Google, driven like a person would: search form,
 * airports and countries, a search, its results, a trip, filters, history.
 * Saves a screenshot of each screen in app/build/screenshots.
 */
@OptIn(ExperimentalTestApi::class)
class AppFlowTest {
    private val dir = File(System.getProperty("screenshots")).apply { mkdirs() }

    private fun ComposeUiTest.shot(name: String) {
        waitForIdle()
        // A sheet or dialog is a second root on top: capture the topmost one.
        ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun ComposeUiTest.waitFor(text: String, substring: Boolean = true, timeout: Long = 30_000) = try {
        waitUntil(timeoutMillis = timeout) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) {
        shot("failed-waiting-for-${text.filter(Char::isLetterOrDigit)}")
        throw e
    }

    private fun ComposeUiTest.click(text: String, index: Int = 0, substring: Boolean = false): SemanticsNodeInteraction =
        onAllNodesWithText(text, substring = substring)[index].apply { runCatching { performScrollTo() }; performClick() }

    @Test
    fun searchExploreAndOpenATrip() = runDesktopComposeUiTest(390 * 2, 844 * 2) {
        File(System.getProperty("flightfinder.data")).deleteRecursively()
        // A resumed lifecycle, as on a phone: navigation waits for it to show a screen.
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
            override val lifecycle: Lifecycle get() = registry
        }
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f), LocalLifecycleOwner provides owner) { App(DemoHttp(latencyMs = 2)) }
        }
        waitFor("Cerca voli")
        shot("01-search")

        click("A: ovunque")
        click("+ Aggiungi", index = 1)
        waitFor("Dove vuoi andare")
        onNode(hasSetTextAction()).performTextInput("londra")
        waitFor("LGW")
        shot("02-place")
        click("LGW")
        onNode(hasSetTextAction()).performTextReplacement("barcel")
        waitFor("BCN")
        click("BCN")
        onNode(hasSetTextAction()).performTextReplacement("spagna")
        waitFor("Tutto il paese")
        shot("03-place-country")
        click("Fine")
        waitFor("Cerca voli")
        shot("04-search-filled")

        click("Cerca voli")
        waitFor("viaggi andata e ritorno", timeout = 60_000)
        waitUntil(timeoutMillis = 60_000) { onAllNodesWithText("Ferma").fetchSemanticsNodes().isEmpty() }
        shot("05-results")
        click("opzioni", substring = true)
        waitFor("Tutte le destinazioni")
        shot("06-trips")
        click("Filtri", substring = true)
        waitFor("Filtra i risultati")
        shot("07-filters")
        click("Mostra risultati")
        click("Andata ·", substring = true)
        waitFor("Vedi offerta su Google Flights")
        shot("08-trip")
        click("Aggiorna prezzo")
        // Same request, same demo flights: the price is confirmed.
        waitFor("Prezzo confermato")
        shot("09-trip-rechecked")

        onAllNodesWithText("←")[0].performClick()
        onAllNodesWithText("←")[0].performClick()
        waitFor("Cerca voli")
        click("Ricerche")
        waitFor("Completata")
        shot("10-history")
    }
}
