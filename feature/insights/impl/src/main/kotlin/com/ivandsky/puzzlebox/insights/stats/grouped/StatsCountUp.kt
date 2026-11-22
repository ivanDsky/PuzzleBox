package com.ivandsky.puzzlebox.insights.stats.grouped

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** A time (m:ss, h:mm:ss, optionally with decimal seconds) first, so its colons are never read as separate numbers; then a plain number. */
private val TokenPattern = Regex("""\d+(?::\d{2}){1,2}(?:\.\d+)?|\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?""")

private fun isTime(token: String) = token.contains(':')

/** The value of a token: a time is its total seconds, any other number is itself. */
private fun valueOf(token: String): BigDecimal =
    if (isTime(token)) {
        token.split(':').fold(BigDecimal.ZERO) { total, part -> total.multiply(BigDecimal(60)).add(BigDecimal(part)) }
    } else {
        BigDecimal(token.replace(",", ""))
    }

private fun decimalsOf(token: String): Int = token.substringAfter('.', "").length

private fun format(token: String, value: BigDecimal): String {
    val decimals = decimalsOf(token)
    val rolled = value.setScale(decimals, RoundingMode.DOWN)
    if (!isTime(token)) {
        val integer = rolled.toBigInteger()
        val whole = if (token.contains(',')) "%,d".format(Locale.ROOT, integer) else integer.toString()
        return if (decimals == 0) whole else whole + "." + rolled.toPlainString().substringAfter('.')
    }
    val seconds = rolled.toBigInteger()
    val minutes = seconds / java.math.BigInteger.valueOf(60)
    val ss = (seconds % java.math.BigInteger.valueOf(60)).toString().padStart(2, '0')
    val fraction = if (decimals == 0) "" else "." + rolled.toPlainString().substringAfter('.')
    val hasHours = token.count { it == ':' } == 2
    return if (hasHours) {
        val h = minutes / java.math.BigInteger.valueOf(60)
        val mm = (minutes % java.math.BigInteger.valueOf(60)).toString().padStart(2, '0')
        "$h:$mm:$ss$fraction"
    } else {
        "$minutes:$ss$fraction"
    }
}

/**
 * [text] with every number in it rolled to [progress] (0..1) of the way from its old value to its own, the rest of
 * the text kept. A number counts from the number in the same place in [from] (the text it replaces), or from 0 when
 * there is none or [from] is empty: "73%" is "36%" halfway up from nothing, "1,284" is "798" halfway up from "312".
 * A time counts as one number, its total seconds, so "1:35" is "0:47" halfway, never minutes and seconds apart. A
 * number keeps its thousands commas and its decimals, a time its shape.
 */
internal fun countUpText(text: String, progress: Float, from: String = ""): String {
    val olds = TokenPattern.findAll(from).map { it.value }.toList()
    var next = 0
    val p = progress.toBigDecimal()
    return TokenPattern.replace(text) { match ->
        val token = match.value
        val old = olds.getOrNull(next++)?.takeIf { isTime(it) == isTime(token) }?.let(::valueOf) ?: BigDecimal.ZERO
        val target = valueOf(token)
        format(token, old.add(target.subtract(old).multiply(p)))
    }
}
