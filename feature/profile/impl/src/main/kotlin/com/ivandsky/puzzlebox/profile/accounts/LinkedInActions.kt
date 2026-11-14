package com.ivandsky.puzzlebox.profile.accounts

/**
 * What the LinkedIn row can ask for. A null [onConnect] means the LinkedIn feature is off, so the row is not drawn;
 * [onDeleteSavedBoards] is offered only with cloud boards on.
 */
internal class LinkedInActions(
    val onConnect: (() -> Unit)?,
    val onResume: () -> Unit,
    val onDisconnect: () -> Unit,
    val onDeleteSavedBoards: () -> Unit,
)
