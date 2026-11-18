package com.ivandsky.puzzlebox.profile.whatsnew

// A fixed English table: the page's dates read the same on every device language, as the notes themselves are English.
private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

// Month 01-12 and day 01-31 only: a real calendar date is not required. The notes file's headings use the same pattern.
internal const val ISO_DATE_PATTERN = """(\d{4})-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])"""
private val isoDate = Regex(ISO_DATE_PATTERN)

/** `2026-11-18` as `18 Nov 2026`. @throws IllegalArgumentException when [iso] is not `yyyy-mm-dd`: a bad date is never shown guessed. */
internal fun formatReleaseDate(iso: String): String {
    val match = isoDate.matchEntire(iso) ?: throw IllegalArgumentException("not a yyyy-mm-dd date: `$iso`")
    val (year, month, day) = match.destructured
    return "${day.toInt()} ${months[month.toInt() - 1]} $year"
}
