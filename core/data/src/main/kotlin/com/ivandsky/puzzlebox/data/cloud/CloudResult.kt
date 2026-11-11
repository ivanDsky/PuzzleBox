package com.ivandsky.puzzlebox.data.cloud

/** The typed outcome of a cloud call; no backend type and no exception crosses the seam. */
sealed interface CloudResult<out T> {
    data class Ok<T>(val value: T) : CloudResult<T>

    /** Unavailable or timed out: the outcome is unknown, and repeating the call is safe. */
    data object Offline : CloudResult<Nothing>

    /** The project's daily quota is spent. */
    data object Exhausted : CloudResult<Nothing>

    /** The backend's rules refused the call. */
    data object Denied : CloudResult<Nothing>

    /** The signed-in account is not the one the call was made for; nothing was written. */
    data object WrongAccount : CloudResult<Nothing>
}
