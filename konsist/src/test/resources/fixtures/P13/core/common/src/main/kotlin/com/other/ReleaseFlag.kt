package com.other

/** Named ReleaseFlag outside the central package and implementing no flag supertype: not a flag enum. */
enum class ReleaseFlag(val label: String, val releaseDefault: Boolean) {
    ElsewhereUnread(label = "Read nowhere", releaseDefault = true),
}
