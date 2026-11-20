package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.insights.domain.recap.FriendsStanding
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/** The friends card's contract dataset: nine friends around Zip, thirty around Mini Sudoku, a year of Queens. */
internal object FriendsFixtures {
    private val zip = RecapFixtures.zip
    private val queens = RecapFixtures.queens
    private val sudoku = RecapFixtures.sudoku

    val gameName: (GameId) -> String = { if (it == sudoku) "Mini Sudoku" else RecapFixtures.gameName(it) }

    val brands: (GameId) -> GameBrand = {
        when (it) {
            zip -> GameBrand(AccentSlot.Blue, GameGlyphs.Zip)
            queens -> GameBrand(AccentSlot.Violet, GameGlyphs.Queens)
            else -> GameBrand(AccentSlot.Teal, GameGlyphs.Sudoku)
        }
    }

    val nine = listOf("Maya R", "Dev", "Jo", "Pia S", "Tom", "Lee", "Sam K", "Rae", "Oli")
    val thirty = (1..30).map { "Friend $it" }

    val loaded = RecapCard.Friends(FriendsStanding.Loaded(zip, 412.0, friendCount = 9, friendNames = nine))
    val longest = RecapCard.Friends(FriendsStanding.Loaded(sudoku, 1_980.0, friendCount = 30, friendNames = thirty))
    val year = RecapCard.Friends(FriendsStanding.Loaded(queens, 212.0, weeks = 14))
    val settling = RecapCard.Friends(FriendsStanding.Settling(LocalDate.of(2026, 11, 4), friendCount = 9, friendNames = nine))
    val reading = RecapFixtures.friendsReading
    val unavailable = RecapFixtures.friendsUnavailable

    /** The friends card at the sixth place of a full story, so the tablet list shows its neighbours. */
    fun story(friends: RecapCard.Friends) = readyOf(
        RecapFixtures.solved,
        RecapFixtures.fastest,
        RecapFixtures.streak,
        RecapFixtures.moments,
        friends,
        RecapFixtures.totalTime,
    ).copy(initialPage = 4)
}
