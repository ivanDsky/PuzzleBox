package com.ivandsky.puzzlebox.network.linkedin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pins the exact strings LinkedIn accepts; a wrong byte is a 400 or a silent 404. */
class VoyagerQueriesTest {

    private val urn = "urn:li:fsd_game:(ACoAAFAKE,3,816)"

    @Test
    fun `the three query ids are the captured ones`() {
        assertEquals("voyagerIdentityDashGames.e3326d1fde9a5931eef881827f23ded9", VoyagerQueries.TODAYS_GAME)
        assertEquals(
            "voyagerIdentityDashGameEndPage.b51bd3075ade52e7e7fb4e1e364df72f",
            VoyagerQueries.END_PAGE,
        )
        assertEquals(
            "voyagerIdentityDashGameConnectionsEntities.a5fed1d462445e698d896bd5563fed9c",
            VoyagerQueries.CONNECTIONS,
        )
    }

    @Test
    fun `today's game variables are literal`() {
        assertEquals("(gameTypeId:3)", VoyagerQueries.todaysGameVariables(3))
    }

    @Test
    fun `the end-page variables encode the urn value and keep the tuple literal`() {
        assertEquals(
            "(gameUrn:urn%3Ali%3Afsd_game%3A%28ACoAAFAKE%2C3%2C816%29)",
            VoyagerQueries.endPageVariables(urn),
        )
    }

    @Test
    fun `the connections variables carry start 0, count 50 and the delta`() {
        val encoded = "urn%3Ali%3Afsd_game%3A%28ACoAAFAKE%2C3%2C816%29"
        assertEquals("(gameUrn:$encoded,start:0,count:50,delta:3)", VoyagerQueries.connectionsVariables(urn, 3))
        assertEquals("(gameUrn:$encoded,start:0,count:50,delta:0)", VoyagerQueries.connectionsVariables(urn, 0))
        assertEquals("(gameUrn:$encoded,start:0,count:50,delta:7)", VoyagerQueries.connectionsVariables(urn, 7))
    }

    @Test
    fun `a profile id with url-safe punctuation stays literal in the urn value`() {
        assertEquals(
            "(gameUrn:urn%3Ali%3Afsd_game%3A%28ACoAA-_xyz%2C6%2C1%29)",
            VoyagerQueries.endPageVariables(VoyagerQueries.gameUrn("ACoAA-_xyz", 6, 1)),
        )
    }

    @Test
    fun `a game urn is built from its three parts`() {
        assertEquals("urn:li:fsd_game:(ACoAAFAKE,3,816)", VoyagerQueries.gameUrn("ACoAAFAKE", 3, 816))
    }

    @Test
    fun `a game urn round-trips to its profile id`() {
        val profileId = "ACoAA-_xyz"
        assertEquals(profileId, VoyagerQueries.profileIdOf(VoyagerQueries.gameUrn(profileId, 6, 812)))
        assertNull(VoyagerQueries.profileIdOf("urn:li:fsd_game:(only-one)"))
        assertNull(VoyagerQueries.profileIdOf(""))
        assertNull(VoyagerQueries.profileIdOf("urn:li:fsd_profile:ACoAAFAKE"))
    }
}
