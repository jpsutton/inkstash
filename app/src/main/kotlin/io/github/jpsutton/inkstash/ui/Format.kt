package io.github.jpsutton.inkstash.ui

import io.github.jpsutton.inkstash.data.Location
import io.github.jpsutton.inkstash.data.LocationKind

fun Location.kindLabel(): String = when (kind) {
    LocationKind.GENERAL -> "Main collection"
    LocationKind.POOL -> "Pool"
    LocationKind.TEMP_SET -> "Temp set"
}

fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"
