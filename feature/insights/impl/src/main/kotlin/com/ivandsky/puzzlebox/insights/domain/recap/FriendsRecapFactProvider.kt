package com.ivandsky.puzzlebox.insights.domain.recap

/**
 * The Best game among friends card, from the friends answer [RecapInput.friends] carries. It reads no network itself:
 * [ResolveFriendsStanding] has already, outside the local providers, so this provider is as quick as they are. Until
 * the answer lands, and when it says the period has no friends to compare with, there is no card (A12).
 */
internal object FriendsRecapFactProvider : RecapFactProvider {

    override suspend fun cards(input: RecapInput): List<RecapCard> =
        when (val friends = input.friends) {
            is FriendsStanding.Shown -> listOf(RecapCard.Friends(friends))
            FriendsStanding.Reading, FriendsStanding.NotApplicable -> emptyList()
        }
}
