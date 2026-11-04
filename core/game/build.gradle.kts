// Pure-Kotlin contracts module (A16): zero Android, zero Compose, zero external
// dependencies — every game (games/<id>) and both UI layers compile against only this.
plugins {
    alias(libs.plugins.puzzlebox.jvm.library)
}

dependencies {
    // GameId is part of the public contract surface (GameDefinition.id, Puzzle.gameId).
    api(projects.core.model)
}
