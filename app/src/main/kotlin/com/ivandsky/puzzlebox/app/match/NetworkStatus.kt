package com.ivandsky.puzzlebox.app.match

import kotlinx.coroutines.flow.Flow

/** Whether the device currently has a network connection. The app binds the Android implementation. */
interface NetworkStatus {
    /** The current state first, then every change; collectors on the process scope see the false-to-true edge. */
    val online: Flow<Boolean>
}
