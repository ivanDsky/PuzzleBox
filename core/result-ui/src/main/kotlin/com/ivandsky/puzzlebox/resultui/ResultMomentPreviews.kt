package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.LocalDate
import java.util.Locale

// Previews of the moment card and of each main-button case under the hero card (L1, L3, L4), light and dark.

private val Brand = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)
private val Maya = ResultPrimaryAction.ChallengeFriend("u1", "Maya")
private val Code = ShareCode.parse("ABC123")!!

private val AllMoments = listOf(
    Moment.PersonalBest,
    Moment.TopThreeFriends(2),
    Moment.Streak(7),
    Moment.FullSetDone(),
)

@Composable
private fun NextThingPreview(facts: ResultHeroFacts) {
    val model = resultHeroModel(facts, Locale.US)
    PuzzleBoxTheme {
        Column(Modifier.width(360.dp).padding(Spacing.lg)) {
            ResultHeroCard(
                model = model,
                brand = Brand,
                actions = {
                    ResultHeroActions(
                        model.primary,
                        onPrimary = {},
                        onShare = {},
                        onPlayAgain = {},
                        challenge = model.challenge,
                        onChallenge = {},
                    )
                },
            )
            ResultMomentCard(model.moments, Brand)
        }
    }
}

private val NewBest = heroSampleFacts(verdict = RecordVerdict.NewBest, timeMs = 58_000L, previous = BestScore(65_000L, 0))

/** Three moments under the hero: the fourth is cut, the strongest first. */
@PuzzleBoxPreviews
@Composable
internal fun ResultMomentCardPreview() {
    NextThingPreview(NewBest.copy(moments = AllMoments))
}

/** The next daily as the main button, with the Challenge row under it. */
@PuzzleBoxPreviews
@Composable
internal fun ResultChallengeRowPreview() {
    NextThingPreview(NewBest.copy(moments = listOf(Moment.PassedFriend("u1", "Maya")), challenge = Maya))
}

/** The challenge is the main button, so there is no second Challenge row. */
@PuzzleBoxPreviews
@Composable
internal fun ResultChallengeMainPreview() {
    NextThingPreview(NewBest.copy(primary = Maya, challenge = Maya))
}

@PuzzleBoxPreviews
@Composable
internal fun ResultMissedDayPreview() {
    NextThingPreview(NewBest.copy(primary = ResultPrimaryAction.MissedDay(GameId("tango"), LocalDate.of(2026, 11, 3), "Tango")))
}

@PuzzleBoxPreviews
@Composable
internal fun ResultFriendPuzzlePreview() {
    NextThingPreview(NewBest.copy(primary = ResultPrimaryAction.FriendPuzzle(Code, GameId("tango"), "Maya")))
}

@PuzzleBoxPreviews
@Composable
internal fun ResultCommunityPuzzlePreview() {
    NextThingPreview(NewBest.copy(primary = ResultPrimaryAction.CommunityPuzzle(Code, GameId("tango"), "Sam Okafor")))
}
