package com.ivandsky.puzzlebox.game

/**
 * Typed decode outcome for every artifact reader (A19): callers branch, they never catch.
 * Used by [GameDefinition.decode] and [GameSessionCodec.deserialize].
 */
sealed interface DecodeResult<out T> {
    data class Ok<T>(val value: T) : DecodeResult<T>
    data class Err(val error: DecodeError) : DecodeResult<Nothing>

    fun getOrNull(): T? = (this as? Ok)?.value
}

inline fun <T, R> DecodeResult<T>.map(transform: (T) -> R): DecodeResult<R> = when (this) {
    is DecodeResult.Ok -> DecodeResult.Ok(transform(value))
    is DecodeResult.Err -> this
}

/**
 * Why an artifact could not be read. Unknown versions are dropped, never reinterpreted;
 * unknown variants are never guessed (ADR-0005).
 */
sealed interface DecodeError {
    /** Bytes written by a newer schema. Drop and take an explicit re-fetch/rebuild path. */
    data class UnsupportedVersion(val found: Int, val maxKnown: Int) : DecodeError

    /** Variant id not shipped by this build. Never guess rules for it (A18). */
    data class UnknownVariant(val id: String) : DecodeError

    /** Structurally valid bytes, but the board size contradicts the pinned variant (I9). */
    data class VariantSizeMismatch(val variant: String, val expected: Int, val actual: Int) : DecodeError

    /** Truncated, out-of-range, or otherwise structurally broken bytes. */
    data class Malformed(val detail: String) : DecodeError
}
