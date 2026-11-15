package com.ivandsky.puzzlebox.network.linkedin

import okhttp3.Request
import okhttp3.RequestBody

internal fun buildPost(url: String, body: RequestBody): Request =
    Request.Builder().url(url).post(body).build()
