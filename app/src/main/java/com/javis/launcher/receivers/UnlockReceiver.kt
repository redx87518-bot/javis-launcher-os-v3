package com.javis.launcher.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

/**
 * Detects screen unlock (ACTION_USER_PRESENT) and sets a flag so
 * HomeActivity can speak the time-based greeting on its next onResume().
 *
 * Must be registered dynamically in HomeActivity because ACTION_USER_PRESENT
 * cannot be received by statically declared receivers on Android 8+.
 */
class UnlockReceiver : BroadcastReceiver() {

    companion object {
        const val PREFS = "javis_unlock"
        const val KEY_PENDING_GREETING = "pending_greeting"

        fun prefs(ctx: Context): SharedPreferences =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** Call from HomeActivity.onResume() — returns true once, then clears the flag. */
        fun consumePendingGreeting(ctx: Context): Boolean {
            val p = prefs(ctx)
            return if (p.getBoolean(KEY_PENDING_GREETING, false)) {
                p.edit().putBoolean(KEY_PENDING_GREETING, false).apply()
                true
            } else false
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            prefs(context).edit().putBoolean(KEY_PENDING_GREETING, true).apply()
        }
    }
}
