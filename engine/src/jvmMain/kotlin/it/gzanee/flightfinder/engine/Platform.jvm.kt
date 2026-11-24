package it.gzanee.flightfinder.engine

import java.text.Normalizer

actual fun nfkd(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD)
