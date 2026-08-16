package com.javis.launcher.util

import android.content.Context
import android.content.SharedPreferences

object ThemeManager {

    private const val PREFS = "javis_theme"
    private const val KEY_THEME = "selected_theme"

    const val THEME_RED = "red"
    const val THEME_BLUE = "blue"
    const val THEME_GREEN = "green"
    const val THEME_PURPLE = "purple"
    const val THEME_ORANGE = "orange"

    val themes = listOf(THEME_RED, THEME_BLUE, THEME_GREEN, THEME_PURPLE, THEME_ORANGE)

    fun themeStyleName(theme: String): String = when (theme) {
        THEME_BLUE -> "Theme.JAVIS.Blue"
        THEME_GREEN -> "Theme.JAVIS.Green"
        THEME_PURPLE -> "Theme.JAVIS.Purple"
        THEME_ORANGE -> "Theme.JAVIS.Orange"
        else -> "Theme.JAVIS"
    }

    fun getTheme(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, THEME_RED) ?: THEME_RED
    }

    fun setTheme(context: Context, theme: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme)
            .apply()
    }

    fun orbColor(theme: String): Int = when (theme) {
        THEME_BLUE -> 0xFF0088FF.toInt()
        THEME_GREEN -> 0xFF00CC55.toInt()
        THEME_PURPLE -> 0xFFAA00FF.toInt()
        THEME_ORANGE -> 0xFFFF8800.toInt()
        else -> 0xFFCC0000.toInt()
    }

    fun primaryColor(theme: String): Int = when (theme) {
        THEME_BLUE -> 0xFF0066CC.toInt()
        THEME_GREEN -> 0xFF00AA44.toInt()
        THEME_PURPLE -> 0xFF8800CC.toInt()
        THEME_ORANGE -> 0xFFCC6600.toInt()
        else -> 0xFFCC0000.toInt()
    }
}
