package com.nethu.calculatorvault.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeUtil {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun mode(ctx: Context): Int = prefs(ctx).getInt("theme", 0)

    fun apply(ctx: Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode(ctx)) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    fun set(ctx: Context, mode: Int) {
        prefs(ctx).edit().putInt("theme", mode).apply()
        apply(ctx)
    }

    fun label(mode: Int) = when (mode) { 1 -> "Light"; 2 -> "Dark"; else -> "System default" }
}
