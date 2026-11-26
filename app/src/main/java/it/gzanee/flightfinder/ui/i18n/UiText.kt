package it.gzanee.flightfinder.ui.i18n

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * Un testo da mostrare, deciso in un ViewModel e tradotto solo nella schermata:
 * una risorsa con i suoi argomenti, oppure un messaggio del motore (in italiano, come nel portale).
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Engine(val message: String) : UiText

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
    }
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.map { if (it is UiText) it.asString() else it }.toTypedArray())
    is UiText.Engine -> engineText(message)
}

/** "1 notte" / "3 notti" nella lingua dell'app. */
@Composable
fun plural(@PluralsRes id: Int, count: Int): String = pluralStringResource(id, count, count)
