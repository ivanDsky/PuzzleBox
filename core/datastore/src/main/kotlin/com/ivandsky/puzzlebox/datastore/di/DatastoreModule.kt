package com.ivandsky.puzzlebox.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.datastore.LinkedInLinkDataSource
import com.ivandsky.puzzlebox.datastore.PuzzleBoxPreferencesDataSource
import com.ivandsky.puzzlebox.datastore.SyncStateDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.dsl.onClose

private val PreferencesScope = named("datastore.preferences.scope")
private val LinkedInScope = named("datastore.linkedin_link.scope")
private val SyncStateScope = named("datastore.cloud_sync_state.scope")

/**
 * The DataStore owns its file for the whole process, so its scope lives as long as the
 * singleton: I/O dispatcher from the provider, SupervisorJob so one failed write never
 * cancels the scope. The Context is resolved here, not inside the file producer, which
 * DataStore runs lazily on first collection, possibly after Koin has closed.
 */
private fun Scope.store(scope: CoroutineScope, fileName: String): DataStore<Preferences> {
    val filesDir = get<Context>().filesDir
    return PreferenceDataStoreFactory.create(scope = scope) { filesDir.resolve(fileName) }
}

val datastoreModule = module {
    single(PreferencesScope) { CoroutineScope(get<DispatcherProvider>().io + SupervisorJob()) } onClose { it?.cancel() }
    single { store(get(PreferencesScope), "puzzlebox.preferences_pb") }
    single { PuzzleBoxPreferencesDataSource(get()) }
    // Two DataStores must never share a file, so the LinkedIn link gets its own.
    single(LinkedInScope) { CoroutineScope(get<DispatcherProvider>().io + SupervisorJob()) } onClose { it?.cancel() }
    single(named("linkedin_link")) { store(get(LinkedInScope), "linkedin_link.preferences_pb") }
    single { LinkedInLinkDataSource(get(named("linkedin_link"))) }
    // Sync bookkeeping never enters a backup (ADR-0029 §7), so it gets a file of its own, excluded by name.
    single(SyncStateScope) { CoroutineScope(get<DispatcherProvider>().io + SupervisorJob()) } onClose { it?.cancel() }
    single(named("cloud_sync_state")) { store(get(SyncStateScope), "cloud_sync_state.preferences_pb") }
    single { SyncStateDataSource(get(named("cloud_sync_state"))) }
}
