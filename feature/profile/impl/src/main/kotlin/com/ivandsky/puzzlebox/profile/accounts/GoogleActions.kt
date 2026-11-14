package com.ivandsky.puzzlebox.profile.accounts

/** What the Google row can ask for: sign in, sign out, and, with cloud sync on, Sync now and Delete my cloud data. */
internal class GoogleActions(
    val onSignIn: () -> Unit,
    val onSignOut: () -> Unit,
    val onSyncNow: () -> Unit,
    val onDeleteCloudData: () -> Unit,
)
