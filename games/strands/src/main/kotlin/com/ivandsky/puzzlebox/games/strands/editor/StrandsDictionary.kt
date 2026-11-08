package com.ivandsky.puzzlebox.games.strands.editor

import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * The editor's word list: words of four or more letters, uppercase A–Z, minus the words in
 * `editor/blocklist.txt`. It loads once, on the first call, by unzipping `editor/enable.txt.gz`,
 * so make that first call off the main thread. The words stay packed in one byte array, about
 * 1.6 MB for ~172k entries, and every lookup is a binary search.
 *
 * Credit: the list is ENABLE2K (Enhanced North American Benchmark LExicon) by Alan Beale and
 * M. Cooper, released into the public domain. Source: enable2k.txt.gz from
 * https://github.com/BartMassey/wordlists, cut to four or more letters, uppercased and sorted.
 * sha256 of the bundled `editor/enable.txt.gz`:
 * 4b9d5f7763bf86e2d9229570bf7b4a538f71ec96ad6f49897e5cf2899bdc1648
 */
object StrandsDictionary {
    private val blocklist: PackedWords by lazy {
        PackedWords.unpack(resource("blocklist.txt").use { it.readBytes() }, skip = null)
    }
    private val words: PackedWords by lazy {
        val text = GZIPInputStream(resource("enable.txt.gz")).use { it.readBytes() }
        PackedWords.unpack(text, skip = blocklist)
    }

    /** Whether [word], uppercase, is a dictionary word. */
    fun contains(word: String): Boolean = words.contains(word)

    /** Whether some dictionary word starts with [prefix], uppercase. */
    fun hasPrefix(prefix: String): Boolean = words.hasPrefix(prefix)

    /** Whether [word], uppercase, may never be a theme word or the spangram. */
    fun isBlocklisted(word: String): Boolean = blocklist.contains(word)

    internal fun contains(word: CharSequence): Boolean = words.contains(word)

    internal fun hasPrefix(prefix: CharSequence): Boolean = words.hasPrefix(prefix)

    private fun resource(name: String): InputStream =
        checkNotNull(StrandsDictionary::class.java.getResourceAsStream("/editor/$name")) {
            "missing resource editor/$name"
        }
}

/** Sorted ASCII words laid end to end in [bytes]; word i spans `starts[i] until starts[i + 1]`. */
private class PackedWords(private val bytes: ByteArray, private val starts: IntArray) {
    private val size: Int get() = starts.size - 1

    fun contains(word: CharSequence): Boolean {
        val i = lowerBound(word)
        return i < size && compare(i, word) == 0
    }

    fun hasPrefix(prefix: CharSequence): Boolean {
        val i = lowerBound(prefix)
        if (i == size) return false
        val start = starts[i]
        if (starts[i + 1] - start < prefix.length) return false
        for (k in prefix.indices) {
            if (bytes[start + k].toInt() != prefix[k].code) return false
        }
        return true
    }

    /** The first index whose word is not less than [key]. */
    private fun lowerBound(key: CharSequence): Int {
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (compare(mid, key) < 0) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun compare(index: Int, key: CharSequence): Int {
        val start = starts[index]
        val length = starts[index + 1] - start
        for (k in 0 until minOf(length, key.length)) {
            val diff = bytes[start + k].toInt() - key[k].code
            if (diff != 0) return diff
        }
        return length - key.length
    }

    /** A view of `bytes[start until end]` as text, so a line can be looked up without a copy. */
    private class Span(private val bytes: ByteArray) : CharSequence {
        var start = 0
        var end = 0
        override val length: Int get() = end - start
        override fun get(index: Int): Char = bytes[start + index].toInt().toChar()
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            String(bytes, start + startIndex, endIndex - startIndex, Charsets.US_ASCII)
    }

    companion object {
        private const val NEWLINE = '\n'.code.toByte()

        /**
         * Packs [text], sorted words one per line, dropping blank lines and any word in [skip].
         * [text] is reused as the scratch buffer.
         */
        fun unpack(text: ByteArray, skip: PackedWords?): PackedWords {
            val starts = IntArray(text.count { it == NEWLINE } + 2)
            val line = Span(text)
            var count = 0
            var write = 0
            var read = 0
            while (read < text.size) {
                var end = read
                while (end < text.size && text[end] != NEWLINE) end++
                line.start = read
                line.end = end
                if (end > read && skip?.contains(line) != true) {
                    text.copyInto(text, destinationOffset = write, startIndex = read, endIndex = end)
                    write += end - read
                    starts[++count] = write
                }
                read = end + 1
            }
            return PackedWords(text.copyOf(write), starts.copyOf(count + 1))
        }
    }
}
