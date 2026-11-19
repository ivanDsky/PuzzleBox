package com.ivandsky.puzzlebox.insights.domain.recap

/**
 * The Best game among friends card, from the friends state [RecapInput.friends] carries. It reads no network itself:
 * [ResolveFriendsStanding] has already decided whether the card applies and, outside the local providers, what it says,
 * so this provider is as quick as they are. Where the state is not applicable there is no card (A12).
 */
internal object FriendsRecapFactProvider : RecapFactProvider {

    override suspend fun cards(input: RecapInput): List<RecapCard> =
        when (val friends = input.friends) {
            is FriendsStanding.Shown -> listOf(RecapCard.Friends(friends))
            FriendsStanding.NotApplicable -> emptyList()
        }
}
