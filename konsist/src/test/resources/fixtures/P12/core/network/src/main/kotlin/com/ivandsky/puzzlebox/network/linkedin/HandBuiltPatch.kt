package com.ivandsky.puzzlebox.network.linkedin

import okhttp3.Request
import okhttp3.RequestBody

internal fun buildPatch(url: String, body: RequestBody): Request =
    Request.Builder().url(url).patch(body).build()
