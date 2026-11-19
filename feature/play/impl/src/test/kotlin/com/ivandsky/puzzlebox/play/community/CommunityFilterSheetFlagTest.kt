package com.ivandsky.puzzlebox.play.community

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.play.PlayFlag
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** How the community filter is reached, [CommunityState.filterSheet], from the debug flag. */
class CommunityFilterSheetFlagTest {

    private val fixture = CommunityFixture()

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    /** Bug it catches: a build with the flag off (or no flag store) getting the sheet, or the flag on leaving the ⋮ menu. */
    @Test
    fun `the filter is a sheet only with its debug flag on`() {
        assertFalse(fixture.viewModel().state.value.filterSheet)
        assertFalse(fixture.viewModel(releaseFlags = FakeReleaseFlags()).state.value.filterSheet)
        assertTrue(fixture.viewModel(releaseFlags = FakeReleaseFlags(PlayFlag.CommunityFilterSheet)).state.value.filterSheet)
    }

    /** Bug it catches: the switch read once at start, so flipping it in Settings needed an app restart; or a flip dropping the picks. */
    @Test
    fun `flipping the flag changes the filter without a new view model and keeps the picks`() {
        val on = MutableStateFlow<Set<ReleaseFlagKey>>(emptySet())
        val viewModel = fixture.viewModel(releaseFlags = object : ReleaseFlags { override val enabled = on })
        viewModel.pickGame(ALPHA)
        viewModel.pickDifficulty(DifficultyChoice.Hard)
        assertFalse(viewModel.state.value.filterSheet)

        on.value = setOf(PlayFlag.CommunityFilterSheet)
        assertTrue(viewModel.state.value.filterSheet)
        assertEquals(ALPHA, viewModel.state.value.game)
        assertEquals(DifficultyChoice.Hard, viewModel.state.value.difficulty)

        on.value = emptySet()
        assertFalse(viewModel.state.value.filterSheet)
    }
}
