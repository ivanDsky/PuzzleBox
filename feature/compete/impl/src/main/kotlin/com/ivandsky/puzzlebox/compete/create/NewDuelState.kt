package com.ivandsky.puzzlebox.compete.create

import com.ivandsky.puzzlebox.compete.create.lineup.LineupEditor

/** Which panel New duel shows. */
internal enum class DuelStep { Create, SignIn }

/** How the sign-in panel's last try went; a dismissed account picker is back to [Idle]. */
internal enum class DuelSignIn { Idle, SigningIn, Offline, Failed }

/** Everything New duel draws. [summary] is the footer text, fixed at the moment the state was built. */
internal data class NewDuelState(
    val draft: DuelDraft,
    val games: List<DuelGame>,
    val summary: String,
    /** The friend a challenge invites, by name, for the form to say so; null for a duel nobody is invited to. */
    val invitee: String?,
    val step: DuelStep,
    val creating: Boolean,
    val notice: DuelNotice?,
    val signIn: DuelSignIn,
    /** Past you is offered: no challenge already names a friend. */
    val selfOffered: Boolean = false,
    /** Who the duel is against; [Opponent.PastYou] only while [selfOffered]. */
    val opponent: Opponent = Opponent.Friend,
    /** The name Past you's avatar takes its initial from: the player's own, "You" while signed out. */
    val selfName: String = "You",
    /** The signed-in player's photo for Past you's avatar; null signed out, without a photo, or with the PastYouPhoto flag off. */
    val selfPhotoUrl: String? = null,
    val self: SelfDuelView = SelfDuelView.initial(),
    /** The lineup editor Customize shows; it is Past you's own rows while Past you is chosen. */
    val lineup: LineupEditor = LineupEditor(),
) {
    val canCreate: Boolean
        get() = !creating && when (opponent) {
            Opponent.Friend -> draft.games.isNotEmpty()
            // Customize's lineup is sent as it stands, so it needs a row and a solved puzzle to draw from; Quick draws by count.
            Opponent.PastYou -> if (self.form.preset == DuelPreset.Custom) lineup.draft.entries.isNotEmpty() && !self.empty else self.canCreate
        }
}
