package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.CustomSlot
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/** An empty Custom body, for a test that builds the route table and never opens the Custom list. */
internal fun testCustomSlot() = CustomSlot(content = { _, _, _ -> })

/** A [MakeOne] over an empty registry and a fake drafts store, for a test that builds the route table and never taps Make one. */
internal fun testMakeOne(navigator: Navigator): MakeOne {
    val games = GameRegistry(emptyList())
    return MakeOne(CreateDraft(games, FakeDraftRepository(), newId = { "draft" }, clock = Instant::now), games, CoroutineScope(Job()), navigator)
}
