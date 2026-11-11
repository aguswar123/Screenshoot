package com.glowshot.app

import android.content.Context

/** User settings (stored in SharedPreferences). */
class Cfg(
    var side: String = "r",   // "l" or "r": which edge the power button is on
    var pos: Int = 30,        // power button centre, % of screen height from the top
    var thick: Int = 8,       // glow thickness
    var wob: Int = 12,        // wobble amount
    var dur: Int = 30,        // glow duration in tenths of a second
    var radius: Int = 44      // screen corner radius in dp
) {
    val durMs: Long get() = dur * 100L

    fun save(c: Context) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("side", side).putInt("pos", pos).putInt("thick", thick)
            .putInt("wob", wob).putInt("dur", dur).putInt("radius", radius).apply()
    }

    companion object {
        private const val FILE = "glowshot"
        const val HOLD_MS = 1000L   // power button is "held" for 1s before the glow starts

        fun load(c: Context): Cfg {
            val s = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            return Cfg(
                s.getString("side", "r") ?: "r", s.getInt("pos", 30), s.getInt("thick", 8),
                s.getInt("wob", 12), s.getInt("dur", 30), s.getInt("radius", 44)
            )
        }
    }
}
