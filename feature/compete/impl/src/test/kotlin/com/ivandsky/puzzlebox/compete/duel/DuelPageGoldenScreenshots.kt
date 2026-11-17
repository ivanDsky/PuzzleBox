package com.ivandsky.puzzlebox.compete.duel

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.compete.reveal.goldenBrandsOf
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.data.match.SelfSeats
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.domain.LegSeen
import com.ivandsky.puzzlebox.domain.MatchView
import com.ivandsky.puzzlebox.domain.Outcome
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val GOLDEN_PHONE = "w411dp-h891dp-420dpi"
private const val GOLDEN_TABLET = "w1280dp-h800dp-land-mdpi"
private const val SETTLE_MS = 2_000L

/**
 * The duel page of a self duel, phone and tablet, light and dark: a duel with a puzzle to play, one where all are
 * played, and the Remove confirm over it. Goldens in `snapshots/duel/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class DuelPagePhoneGoldenScreenshots : DuelPageGoldenScreenshots("phone", PlayLayout.SinglePane)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class DuelPageTabletGoldenScreenshots : DuelPageGoldenScreenshots("tablet", PlayLayout.TwoPane)

abstract class DuelPageGoldenScreenshots(private val device: String, private val layout: PlayLayout) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun startKoinWithTheGoldenGames() {
        startKoin {
            modules(
                module {
                    single { GameRegistry(listOf(TestGames.plugin(ZIP), TestGames.plugin(SUDOKU), hintsFirst(STRANDS))) }
                    single { goldenBrandsOf(ZIP to AccentSlot.Blue, SUDOKU to AccentSlot.Teal, STRANDS to AccentSlot.Pink) }
                },
            )
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    /** [TestGames.plugin] for [game] ranking hints first, the rule the real Strands has. */
    private fun hintsFirst(game: GameId): GamePlugin<*, *, *> {
        val base = TestGames.plugin(game)
        return GamePlugin(
            definition = base.definition,
            rules = base.rules,
            session = base.session,
            score = object : ScoreModel<TestGames.Session> {
                override fun completion(session: TestGames.Session): CompletionRecord? = null
                override val rankOrder: RankOrder = RankOrder.HintsThenTime
            },
            stateOf = base.stateOf,
            initialSession = base.initialSession,
            refOf = base.refOf,
            playAgainSession = base.playAgainSession,
            rendererModel = base.rendererModel,
        )
    }

    @Test fun `your move light`() = page("duel-page-your-move", doneLegs = setOf(0), dark = false)
    @Test fun `your move dark`() = page("duel-page-your-move", doneLegs = setOf(0), dark = true)

    @Test fun `all played light`() = page("duel-page-all-played", doneLegs = setOf(0, 1, 2), dark = false)
    @Test fun `all played dark`() = page("duel-page-all-played", doneLegs = setOf(0, 1, 2), dark = true)

    @Test fun `remove confirm light`() = page("duel-page-remove-confirm", doneLegs = setOf(0), dark = false, confirm = true)
    @Test fun `remove confirm dark`() = page("duel-page-remove-confirm", doneLegs = setOf(0), dark = true, confirm = true)

    private fun page(name: String, doneLegs: Set<Int>, dark: Boolean, confirm: Boolean = false) {
        val state = state(doneLegs)
        rule.showGolden(dark) {
            Column(Modifier.fillMaxSize()) {
                CompactTopBar(
                    title = "Duel",
                    navigationIcon = { IconButton(onClick = {}) { Icon(PuzzleIcons.Back, contentDescription = "Back") } },
                )
                DuelLegs(
                    state = state,
                    layout = layout,
                    now = NOW,
                    time = CompeteTime(Clock.fixed(NOW, ZoneOffset.UTC), Locale.US),
                    actions = LegsActions({}, {}, {}, {}),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            if (confirm) RemoveDuelDialog(onRemove = {}, onKeep = {})
        }
        rule.settleAndCapture(name, device, dark)
    }

    private fun state(doneLegs: Set<Int>): DuelUiState.Participant {
        val legs = listOf(
            MatchLeg(LegRef.Daily(ZIP, DAY, 412), authorUid = null),
            MatchLeg(LegRef.Daily(SUDOKU, DAY, 371), authorUid = null),
            MatchLeg(LegRef.Custom(STRANDS, checkNotNull(ShareCode.parse("KQ47TM"))), authorUid = null),
        )
        val match = Match(
            code = CODE,
            formatVersion = Match.FORMAT_VERSION,
            createdAt = NOW,
            deadlineHours = SelfSeats.NO_DEADLINE_HOURS,
            challenger = Player(SelfSeats.ME, "You", null),
            invitedUid = null,
            opponent = Player(SelfSeats.PAST, SelfSeats.PAST_NAME, null),
            legs = legs,
            includeFriends = false,
            cancelled = false,
            declined = false,
            rematchOf = null,
            progress = mapOf(SelfSeats.PAST to legs.indices.toSet(), SelfSeats.ME to doneLegs),
        )
        val mine = doneLegs.map { index ->
            val (time, hints) = SCORES.getValue(index)
            OutboxResult(CODE, LegResult(SelfSeats.ME, index, time, hints, completedAtMs = 1, gaveUp = false), ResultPostState.Posted, 0)
        }
        val view = MatchView(
            match, SelfSeats.ME, mine, legs.map { LegSeen.Played },
            Outcome(emptyList(), 0, 0, settled = false, verdict = null), local = null,
        )
        val firstSolved = legs.indices.associate { legs[it].ref.wire to FIRST_SOLVES.getValue(it) }
        return DuelUiState.Participant(
            view = view,
            elapsedMs = if (1 in doneLegs) emptyMap() else mapOf(1 to 32_000L),
            firstSolvedAt = firstSolved,
            myName = "Ivan",
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-09T12:00:00Z")
        val DAY: LocalDate = LocalDate.of(2026, 11, 9)
        val CODE = checkNotNull(ShareCode.parse("ABC234"))
        val ZIP = GameId("Zip")
        val SUDOKU = GameId("Mini Sudoku")
        val STRANDS = GameId("Strands")

        val SCORES = mapOf(0 to (38_000L to 0), 1 to (72_000L to 1), 2 to (134_000L to 3))
        val FIRST_SOLVES = mapOf(
            0 to Instant.parse("2026-09-14T09:00:00Z"),
            1 to Instant.parse("2026-06-02T09:00:00Z"),
            2 to Instant.parse("2026-08-21T09:00:00Z"),
        )
    }
}

/** Shows [content] the way the app's root Scaffold does, in the [dark] or light theme, with the compose clock frozen until [settleAndCapture]. */
private fun ComposeContentTestRule.showGolden(dark: Boolean, content: @Composable () -> Unit) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    mainClock.autoAdvance = false
    setContent {
        val config = Configuration(LocalConfiguration.current).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        CompositionLocalProvider(LocalConfiguration provides config) {
            PuzzleBoxTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { content() }
            }
        }
    }
}

@OptIn(ExperimentalRoborazziApi::class)
private fun ComposeContentTestRule.settleAndCapture(name: String, device: String, dark: Boolean) {
    mainClock.advanceTimeBy(SETTLE_MS)
    val file = File(checkNotNull(System.getProperty("user.dir")))
        .resolve("snapshots/duel/$name-$device-${if (dark) "dark" else "light"}.png")
        .canonicalFile
        .also { checkNotNull(it.parentFile).mkdirs() }
    captureScreenRoboImage(file.absolutePath)
}
