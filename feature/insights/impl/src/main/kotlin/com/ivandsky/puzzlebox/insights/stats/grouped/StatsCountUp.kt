package com.ivandsky.puzzlebox.insights.stats.grouped

private val NumberPattern = Regex("""\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?""")

/**
 * [text] with every number in it rolled to [progress] (0..1) of its value, the rest of the text kept: "1:35" is
 * "0:17" halfway, "73%" is "36%". A number keeps its thousands commas and its decimals, and after a colon its
 * zero-padded width, so a time keeps its shape all the way up.
 */
internal fun countUpText(text: String, progress: Float): String =
    NumberPattern.replace(text) { match ->
        val digits = match.value
        val grouped = digits.contains(',')
        val clean = digits.replace(",", "")
        val decimals = clean.substringAfter('.', "").length
        val whole = clean.substringBefore('.')
        val afterColon = match.range.first > 0 && text[match.range.first - 1] == ':'
        val value = clean.toBigDecimal().multiply(progress.toBigDecimal()).setScale(decimals, java.math.RoundingMode.DOWN)
        val rolled = value.toPlainString()
        val integer = rolled.substringBefore('.')
        val padded = if (afterColon) integer.padStart(whole.length, '0') else integer
        val withCommas = if (grouped) "%,d".format(java.util.Locale.ROOT, padded.toLong()) else padded
        if (decimals == 0) withCommas else withCommas + "." + rolled.substringAfter('.')
    }
