package com.ivandsky.puzzlebox.data.network

import com.ivandsky.puzzlebox.common.result.AppError
import com.ivandsky.puzzlebox.common.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/**
 * Network-flavoured resultOf: maps [HttpException] to [AppError.Http] FIRST, then runs the
 * same failure families as the common `resultOf` convention (IOException → Network,
 * SerializationException → Parsing, anything else → Unknown, CancellationException rethrown).
 *
 * Deliberately non-suspend: repository callers already execute inside a dispatcher context,
 * and the inline lambda keeps suspend calls (Retrofit, Room) legal at the call site.
 */
inline fun <T> networkResultOf(block: () -> T): AppResult<T> =
    try {
        AppResult.Ok(block())
    } catch (e: HttpException) {
        AppResult.Err(AppError.Http(e.code()))
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
