package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import retrofit2.Retrofit

/**
 * The production reader of the player's own LinkedIn account over [baseUrl], with a client of its own.
 * Tests build it through here too, so they run the wire the app runs.
 */
fun linkedInAccountSource(baseUrl: String, session: LinkedInSessionCookies): LinkedInAccountSource {
    // No converter: the body is read raw, so the shared JSON-converting Retrofit is not needed.
    val api = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(linkedInSessionClient())
        .build()
        .create(LinkedInAccountApi::class.java)
    return VoyagerLinkedInAccountSource(VoyagerGraphql(api, session))
}
