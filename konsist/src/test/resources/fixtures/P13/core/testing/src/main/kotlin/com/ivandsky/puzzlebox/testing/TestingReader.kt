package com.ivandsky.puzzlebox.testing

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag

/** :core:testing main sources only feed tests, so this read gates nothing in a release build. */
val testingOnly = ReleaseFlag.TestingOnly
