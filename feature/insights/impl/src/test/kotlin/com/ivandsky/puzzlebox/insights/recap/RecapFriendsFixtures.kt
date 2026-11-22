package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.FriendsStanding
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import java.time.LocalDate

private val Reading = RecapCard.Friends(FriendsStanding.Reading)
private val Loaded = RecapCard.Friends(FriendsStanding.Loaded(RecapFixtures.queens, points = 1_340.0))
private val Settling = RecapCard.Friends(FriendsStanding.Settling(LocalDate.of(2026, 12, 4)))
private val Unavailable = RecapCard.Friends(FriendsStanding.Unavailable)
private val LoadedYear = RecapCard.Friends(FriendsStanding.Loaded(RecapFixtures.queens, points = 212.0, weeks = 6))

/** The friends read is still out: the card holds its place. */
internal val RecapFixtures.friendsReading: RecapCard.Friends get() = Reading

/** Queens earned the most points among friends. */
internal val RecapFixtures.friendsLoaded: RecapCard.Friends get() = Loaded

/** Day 2 of December: November's friends results settle on the 4th. */
internal val RecapFixtures.friendsSettling: RecapCard.Friends get() = Settling

/** The friends read could not finish. */
internal val RecapFixtures.friendsUnavailable: RecapCard.Friends get() = Unavailable

/** A year's best game among friends from six kept weeks. */
internal val RecapFixtures.friendsLoadedYear: RecapCard.Friends get() = LoadedYear
