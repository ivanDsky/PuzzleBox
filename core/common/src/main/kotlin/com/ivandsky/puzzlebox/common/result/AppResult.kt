package com.ivandsky.puzzlebox.common.result

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.io.IOException

/** Result convention for data operations: a value or a typed error, never a thrown surprise. */
sealed interface AppResult<out T> {
    data class Ok<out T>(val value: T) : AppResult<T>
    data class Err(val error: AppError) : AppResult<Nothing>
}

sealed interface AppError {
    /** No connection, DNS, timeout — usually retryable or a degrade-to-cache signal. */
    data class Network(val cause: IOException) : AppError

    /** Server answered with a non-2xx status. */
    data class Http(val code: Int) : AppError

    /** Payload arrived but could not be parsed. */
    data class Parsing(val cause: SerializationException) : AppError

    /** Anything else; report and log. */
    data class Unknown(val cause: Throwable) : AppError
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Ok) block(value)
    return this
}

inline fun <T> AppResult<T>.onError(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Err) block(error)
    return this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Ok)?.value

/** Runs [block], converting the conventional failure families into [AppError]s. */
suspend fun <T> resultOf(block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Ok(block())
    } catch (e: CancellationException) {
        // WHY: swallowing cancellation corrupts structured concurrency.
        throw e
    } catch (e: IOException) {
        AppResult.Err(AppError.Network(e))
    } catch (e: SerializationException) {
        AppResult.Err(AppError.Parsing(e))
    } catch (e: Exception) {
        AppResult.Err(AppError.Unknown(e))
    }
