package it.gzanee.flightfinder.engine

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCompatibilityMapping

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun nfkd(text: String): String = (text as NSString).decomposedStringWithCompatibilityMapping
