package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.di.createModule
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.gameui.flags.GameUiFlag
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * The Studio's ViewModel gets [GameUiFlag.ModeTiles] from the create Koin module as it opens:
 * off by default, on once the flag is set, and read again by the next Studio that opens.
 */
class EditorModeTilesWiringTest {

    @get:Rule
    val mainRule = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun `each Studio asks ModeTiles as it opens, and it is off by default`() {
        val on = MutableStateFlow(emptySet<ReleaseFlagKey>())
        val flags = object : ReleaseFlags {
            override val enabled = on
        }
        val lanes = object : DispatcherProvider {
            override val main: CoroutineDispatcher = mainRule.testDispatcher
            override val io: CoroutineDispatcher = mainRule.testDispatcher
            override val default: CoroutineDispatcher = mainRule.testDispatcher
        }
        val koin = koinApplication {
            modules(
                createModule,
                module {
                    single<ReleaseFlags> { flags }
                    single<DispatcherProvider> { lanes }
                    single<DraftRepository> { FakeDraftRepository() }
                    single { GameRegistry(emptyList()) }
                    single { GameEditorRegistry(emptyList()) }
                    single { GamePlayRegistry(emptyList()) }
                    single<SharedPuzzleRepository> { FakeSharedPuzzleRepository() }
                    single<PublishedPuzzleRepository> { FakePublishedPuzzleRepository() }
                    single<IdentityService> { nobodySignedIn() }
                    single<GoogleIdTokenProvider> { noGoogleAccount }
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository() }
                },
            )
        }.koin
        fun studio() = koin.get<EditorViewModel>(parameters = { parametersOf(EditorKey("draft-1")) })

        assertFalse(GameUiFlag.ModeTiles.releaseDefault)
        assertFalse(studio().modeTiles)

        on.value = setOf(GameUiFlag.ModeTiles)
        assertTrue(studio().modeTiles)
    }
}
