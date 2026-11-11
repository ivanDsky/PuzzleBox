package com.ivandsky.puzzlebox.model

/**
 * Sealed replay bytes of one solve. Opaque: only the replay codec reads inside. Content-equal.
 *
 * WHY a private constructor with [of] as the only way in: an oversized or empty replay must
 * never reach storage, the outbox or the wire, so the size check cannot be skipped.
 */
class SolveReplay private constructor(private val bytes: ByteArray) {

    val size: Int get() = bytes.size

    fun toByteArray(): ByteArray = bytes.copyOf()

    override fun equals(other: Any?): Boolean =
        other is SolveReplay && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    companion object {
        /** 12 KiB per replay (A12). */
        const val MAX_BYTES: Int = 12_288

        /** Null when bytes is empty or longer than [MAX_BYTES]. Copies the array so the caller's array cannot alias the sealed bytes. */
        fun of(bytes: ByteArray): SolveReplay? =
            if (bytes.isEmpty() || bytes.size > MAX_BYTES) null else SolveReplay(bytes.copyOf())
    }
}
