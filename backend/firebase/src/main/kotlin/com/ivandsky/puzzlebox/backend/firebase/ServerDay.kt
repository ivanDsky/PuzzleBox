package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.auth.FirebaseAuth
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.tasks.await

/** How long a publish waits for the server's day before it stays [PublishResult.Rejected]. */
internal val SERVER_DAY_TIMEOUT: Duration = 10.seconds

/**
 * The server's current UTC day, as `yyyy-MM-dd`, or null when no one is signed in. It is read from
 * the `iat` claim of a freshly issued ID token, which Firebase Auth stamps from its own clock:
 * the app already holds that token, so no rules surface is involved.
 */
internal suspend fun firebaseServerDay(): String? {
    val user = FirebaseAuth.getInstance().currentUser ?: return null
    val issuedAtSeconds = user.getIdToken(true).await().issuedAtTimestamp
    return Instant.ofEpochSecond(issuedAtSeconds).atZone(ZoneOffset.UTC).toLocalDate().toString()
}
