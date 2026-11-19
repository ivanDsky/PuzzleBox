package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.history.flags.HistoryFlag
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** RanksTuneBoardOnly decides whether the Tune sheet drops its Defaults block (backlog #1547); off in release. */
class RanksViewModelTuneFlagTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun hub() = RanksTestHub()

    @Test
    fun `flag on keeps the Defaults out of the sheet`() {
        assertTrue(hub().viewModel(releaseFlags = FakeReleaseFlags(HistoryFlag.RanksTuneBoardOnly)).tuneBoardOnly)
    }

    @Test
    fun `flag off and no flags keep the Defaults in the sheet`() {
        assertFalse(hub().viewModel(releaseFlags = FakeReleaseFlags()).tuneBoardOnly)
        assertFalse(hub().viewModel().tuneBoardOnly)
    }
}
