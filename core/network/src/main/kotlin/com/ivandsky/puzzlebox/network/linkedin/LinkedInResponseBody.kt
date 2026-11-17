package com.ivandsky.puzzlebox.network.linkedin

import okhttp3.ResponseBody
import retrofit2.Response

internal const val HTTP_OK = 200
internal const val HTTP_CONFLICT = 409
private const val BODY_CAP_BYTES = 1_048_576L

/** The body as text, or null when it is longer than the cap (1 MB). Always closes the body. */
internal fun ResponseBody.cappedText(): String? = use {
    if (it.contentLength() > BODY_CAP_BYTES) return@use null
    val source = it.source()
    // Asking for one byte past the cap buffers it only when the body is over: the cap is never crossed unseen.
    source.request(BODY_CAP_BYTES + 1)
    if (source.buffer.size > BODY_CAP_BYTES) null else source.readUtf8()
}

/** Closes whatever body [this] holds without reading it. */
internal fun Response<ResponseBody>.closeBody() {
    errorBody()?.close()
    body()?.close()
}

/** The body of a 200, or empty: any other status is classified without reading it. */
internal fun Response<ResponseBody>.successText(): String {
    if (code() != HTTP_OK) {
        closeBody()
        return ""
    }
    return body()?.cappedText().orEmpty()
}
