package com.ivandsky.puzzlebox.create

import java.time.Duration
import java.time.Instant

/** "[verb] just now", "[verb] 5 min ago", "[verb] 2 h ago", "[verb] 3 d ago", aged from [at]. */
internal fun ageLabel(verb: String, at: Instant, now: Instant): String {
    val age = Duration.between(at, now).coerceAtLeast(Duration.ZERO)
    return when {
        age.toMinutes() < 1 -> "$verb just now"
        age.toHours() < 1 -> "$verb ${age.toMinutes()} min ago"
        age.toDays() < 1 -> "$verb ${age.toHours()} h ago"
        else -> "$verb ${age.toDays()} d ago"
    }
}
