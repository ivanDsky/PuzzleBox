package com.ivandsky.puzzlebox.app

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources

/**
 * [base] whose framework bool `config_enableHapticTextHandle` reads [plays]: true as Pixel builds set it, false as stock
 * Android ships it. Only that id is faked, so a misspelt lookup in the code under test finds nothing and reads false.
 */
internal fun textHandleTickContext(base: Context, plays: Boolean): Context = object : ContextWrapper(base) {
    private val flag = base.resources.getIdentifier("config_enableHapticTextHandle", "bool", "android")
        .also { check(it != 0) { "the framework resources lack config_enableHapticTextHandle" } }

    // The deprecated constructor is the public way to override one framework value on otherwise real resources.
    @Suppress("DEPRECATION")
    private val fake = object : Resources(base.assets, base.resources.displayMetrics, base.resources.configuration) {
        override fun getBoolean(id: Int): Boolean = if (id == flag) plays else super.getBoolean(id)
    }

    override fun getResources(): Resources = fake
}
