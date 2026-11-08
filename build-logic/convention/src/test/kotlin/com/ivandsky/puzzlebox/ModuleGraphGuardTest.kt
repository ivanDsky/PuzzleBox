package com.ivandsky.puzzlebox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Table-driven cases for every arm of [forbiddenEdgeReason], including the ADR-0021 `tools:*` arms. */
class ModuleGraphGuardTest {
    @Test
    fun nothingOutsideToolsDependsOnATool() {
        assertEquals(
            "nothing may depend on a tools:* module (ADR-0021)",
            forbiddenEdgeReason(":app", ":tools:puzzle-cli", "implementation"),
        )
        assertEquals(
            "nothing may depend on a tools:* module (ADR-0021)",
            forbiddenEdgeReason(":core:game", ":tools:puzzle-cli", "testImplementation"),
        )
    }

    @Test
    fun toolMayDependOnCoreGameAndNonUiGames() {
        assertNull(forbiddenEdgeReason(":tools:puzzle-cli", ":core:game", "implementation"))
        assertNull(forbiddenEdgeReason(":tools:puzzle-cli", ":games:zip", "implementation"))
    }

    @Test
    fun toolRejectsEveryOtherModuleFamily() {
        assertEquals(
            "tools may depend on :core:game, non-UI games:* and test-only :core:testing (ADR-0021)",
            forbiddenEdgeReason(":tools:puzzle-cli", ":games:zip-ui", "implementation"),
        )
        assertEquals(
            "tools may depend on :core:game, non-UI games:* and test-only :core:testing (ADR-0021)",
            forbiddenEdgeReason(":tools:puzzle-cli", ":core:data", "implementation"),
        )
        assertEquals(
            "tools may depend on :core:game, non-UI games:* and test-only :core:testing (ADR-0021)",
            forbiddenEdgeReason(":tools:puzzle-cli", ":core:game-ui", "implementation"),
        )
        assertEquals(
            "tools may depend on :core:game, non-UI games:* and test-only :core:testing (ADR-0021)",
            forbiddenEdgeReason(":tools:puzzle-cli", ":core:designsystem", "implementation"),
        )
        assertEquals(
            "tools may depend on :core:game, non-UI games:* and test-only :core:testing (ADR-0021)",
            forbiddenEdgeReason(":tools:puzzle-cli", ":feature:create:impl", "implementation"),
        )
        assertEquals(
            "tools may depend on :core:game, non-UI games:* and test-only :core:testing (ADR-0021)",
            forbiddenEdgeReason(":tools:puzzle-cli", ":backend:firebase", "implementation"),
        )
        assertEquals(
            "tools may depend on :core:game, non-UI games:* and test-only :core:testing (ADR-0021)",
            forbiddenEdgeReason(":tools:puzzle-cli", ":app", "implementation"),
        )
    }

    @Test
    fun toolCoreTestingIsTestOnly() {
        assertNull(forbiddenEdgeReason(":tools:puzzle-cli", ":core:testing", "testImplementation"))
        assertNotNull(forbiddenEdgeReason(":tools:puzzle-cli", ":core:testing", "implementation"))
    }

    @Test
    fun preExistingArmsKeepTheirReasons() {
        // konsist: zero edges in either direction
        assertEquals(
            "konsist reads sources as files; it has no project dependencies",
            forbiddenEdgeReason(":konsist", ":core:model", "implementation"),
        )
        assertNull(forbiddenEdgeReason(":konsist", ":konsist", "implementation"))
        assertEquals(
            "nothing may depend on the verification module :konsist",
            forbiddenEdgeReason(":app", ":konsist", "testImplementation"),
        )
        assertNull(forbiddenEdgeReason(":backend:firebase", ":backend:firebase", "implementation"))

        // composition root
        assertEquals(
            "nothing may depend on the composition root :app",
            forbiddenEdgeReason(":core:model", ":app", "implementation"),
        )
        assertNull(forbiddenEdgeReason(":app", ":core:model", "implementation"))

        // core:testing is test-configuration-only
        assertEquals(
            ":core:testing is reachable only from test configurations",
            forbiddenEdgeReason(":feature:create:impl", ":core:testing", "implementation"),
        )
        assertNull(forbiddenEdgeReason(":feature:create:impl", ":core:testing", "testImplementation"))

        // core stays in core
        assertEquals(
            "core modules may depend on core modules only",
            forbiddenEdgeReason(":core:data", ":feature:create:impl", "implementation"),
        )
        assertNull(forbiddenEdgeReason(":core:data", ":core:model", "implementation"))

        // features reach games only through the registries
        assertEquals(
            "features reach games only through core:game / core:game-ui registries",
            forbiddenEdgeReason(":feature:create:impl", ":games:zip", "implementation"),
        )
        assertNull(forbiddenEdgeReason(":feature:create:impl", ":core:game", "implementation"))

        // feature api modules are narrow
        assertEquals(
            "feature api modules may depend on :core:navigation and :core:model only",
            forbiddenEdgeReason(":feature:create:api", ":core:game", "implementation"),
        )
        assertNull(forbiddenEdgeReason(":feature:create:api", ":core:navigation", "implementation"))

        // feature impl modules are independent
        assertEquals(
            "feature impl modules are independent; depend on the other feature's :api",
            forbiddenEdgeReason(":feature:create:impl", ":feature:solve:impl", "implementation"),
        )
        assertNull(forbiddenEdgeReason(":feature:create:impl", ":feature:solve:api", "implementation"))

        // games must not know about features
        assertEquals(
            "games must not know about features",
            forbiddenEdgeReason(":games:zip", ":feature:create:impl", "implementation"),
        )
        assertNull(forbiddenEdgeReason(":games:zip-ui", ":games:zip", "implementation"))

        // a game module may depend on no other game
        assertEquals(
            "a game module may depend on no other game (only games:x-ui -> games:x)",
            forbiddenEdgeReason(":games:zip", ":games:tango", "implementation"),
        )
        assertNull(forbiddenEdgeReason(":games:zip", ":core:game", "implementation"))
    }

    @Test
    fun konsistArmsWinOverTheToolsArms() {
        assertEquals(
            "konsist reads sources as files; it has no project dependencies",
            forbiddenEdgeReason(":konsist", ":tools:puzzle-cli", "implementation"),
        )
        assertEquals(
            "nothing may depend on the verification module :konsist",
            forbiddenEdgeReason(":tools:puzzle-cli", ":konsist", "implementation"),
        )
    }
}
