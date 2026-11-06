package com.ivandsky.puzzlebox.network

import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.NytTransport
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Maps one NYT GET onto [FetchResult]: 2xx with a body → [FetchResult.Ok]; 404 →
 * [FetchError.NotFound]; any other status, an empty body, a timeout or an IO failure →
 * [FetchError.Unavailable]. Never throws (bar cancellation) and never parses game data.
 */
internal class NytHttpTransport(private val api: NytApi) : NytTransport {

    override suspend fun get(path: String): FetchResult<String> = try {
        val response = api.get(path)
        when {
            response.isSuccessful -> {
                val text = response.body()?.use { it.string() }.orEmpty()
                if (text.isEmpty()) {
                    FetchResult.Err(FetchError.Unavailable("$path: empty ${response.code()} body"))
                } else {
                    FetchResult.Ok(text)
                }
            }
            else -> {
                response.errorBody()?.close()
                val detail = "$path: HTTP ${response.code()}"
                if (response.code() == HTTP_NOT_FOUND) {
                    FetchResult.Err(FetchError.NotFound(detail))
                } else {
                    FetchResult.Err(FetchError.Unavailable(detail))
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        FetchResult.Err(FetchError.Unavailable("$path: transport ${e.javaClass.simpleName}"))
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
