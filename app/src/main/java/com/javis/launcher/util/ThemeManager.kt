package com.javis.launcher.util

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

object ThemeManager {

    private const val PREFS = "javis_theme"
    private const val KEY_THEME = "selected_theme"
    private const val KEY_MODE = "theme_mode"

    const val THEME_RED = "red"
    const val THEME_BLUE = "blue"
    const val THEME_GREEN = "green"
    const val THEME_PURPLE = "purple"
    const val THEME_ORANGE = "orange"

    const val MODE_DARK = "dark"
    const val MODE_LIGHT = "light"

    val themes = listOf(THEME_RED, THEME_BLUE, THEME_GREEN, THEME_PURPLE, THEME_ORANGE)

    fun themeStyleName(theme: String): String = when (theme) {
        THEME_BLUE -> "Theme_JAVIS_Blue"
        THEME_GREEN -> "Theme_JAVIS_Green"
        THEME_PURPLE -> "Theme_JAVIS_Purple"
        THEME_ORANGE -> "Theme_JAVIS_Orange"
        else -> "Theme_JAVIS"
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

    fun getMode(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, MODE_DARK) ?: MODE_DARK
    }

    fun setMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode)
            .apply()
    }

    fun isDarkMode(context: Context): Boolean {
        return getMode(context) == MODE_DARK
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

    fun backgroundColor(mode: String): Int {
        return if (mode == MODE_LIGHT) Color.WHITE else Color.parseColor("#050510")
    }

    fun surfaceColor(mode: String): Int {
        return if (mode == MODE_LIGHT) Color.parseColor("#F5F5F5") else Color.parseColor("#0D0D20")
    }

    fun cardColor(mode: String): Int {
        return if (mode == MODE_LIGHT) Color.WHITE else Color.parseColor("#12122A")
    }

    fun textPrimary(mode: String): Int {
        return if (mode == MODE_LIGHT) Color.BLACK else Color.parseColor("#E8E8FF")
    }

    fun textSecondary(mode: String): Int {
        return if (mode == MODE_LIGHT) Color.DKGRAY else Color.parseColor("#8888AA")
    }
}
