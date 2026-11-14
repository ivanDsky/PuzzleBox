package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** The one call that removes the Firestore disk cache; a seam so the start-up step runs without a real Firestore. */
internal interface FirestoreCacheClearer {
    /** Must run before the Firestore instance starts: the SDK refuses once any operation has touched it. */
    suspend fun clear()
}

internal class SdkFirestoreCacheClearer : FirestoreCacheClearer {
    override suspend fun clear() {
        FirebaseFirestore.getInstance().clearPersistence().await()
    }
}
