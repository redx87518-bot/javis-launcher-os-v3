package com.javis.launcher.ui.home

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.javis.launcher.JavisApplication
import com.javis.launcher.R
import com.javis.launcher.receivers.UnlockReceiver
import com.javis.launcher.ui.alarms.AlarmsActivity
import com.javis.launcher.ui.chat.ChatActivity
import com.javis.launcher.ui.contacts.ContactsActivity
import com.javis.launcher.ui.memory.MemoryActivity
import com.javis.launcher.ui.settings.SettingsActivity
import com.javis.launcher.ui.voice.VoiceActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : AppCompatActivity() {

    private val memory get() = JavisApplication.instance.memoryEngine
    private val voice get() = JavisApplication.instance.voiceEngine

    private lateinit var tvGreeting: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvStatusLine: TextView
    private lateinit var rvFavoriteApps: RecyclerView
    private lateinit var tvProviderStatus: TextView

    // Dynamic receiver for screen-unlock detection (ACTION_USER_PRESENT requires dynamic registration on Android 8+)
    private val unlockReceiver = UnlockReceiver()
    private var receiverRegistered = false

    // Debounce: only speak the greeting once per unlock session, not on every onResume()
    private var lastGreetedAtMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        tvGreeting = findViewById(R.id.tv_greeting)
        tvTime = findViewById(R.id.tv_time)
        tvDate = findViewById(R.id.tv_date)
        tvStatusLine = findViewById(R.id.tv_status_line)
        rvFavoriteApps = findViewById(R.id.rv_favorite_apps)
        tvProviderStatus = findViewById(R.id.tv_provider_status)

        setupClock()
        setupGreeting()
        setupFavoriteApps()
        setupNavButtons()
    }

    override fun onStart() {
        super.onStart()
        // Register dynamically — only way to receive USER_PRESENT on Android 8+
        if (!receiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
            registerReceiver(unlockReceiver, filter)
            receiverRegistered = true
        }
    }

    override fun onStop() {
        super.onStop()
        if (receiverRegistered) {
            unregisterReceiver(unlockReceiver)
            receiverRegistered = false
        }
    }

    override fun onResume() {
        super.onResume()
        setupGreeting()
        setupFavoriteApps()
        maybeDeliverUnlockGreeting()
    }

    // ─── Unlock Greeting ──────────────────────────────────────────────────
    private fun maybeDeliverUnlockGreeting() {
        val now = System.currentTimeMillis()
        // Guard: don't speak if we spoke within the last 2 minutes (prevents double-fire)
        if (now - lastGreetedAtMs < 2 * 60 * 1000L) return
        if (!UnlockReceiver.consumePendingGreeting(this)) return

        lastGreetedAtMs = now
        lifecycleScope.launch {
            // Small delay so the screen is fully visible before speaking
            delay(600)
            val greeting = buildUnlockGreeting()
            tvStatusLine.text = greeting
            if (voice.isReady()) {
                voice.speak(greeting)
            }
        }
    }

    private suspend fun buildUnlockGreeting(): String {
        val name = memory.getNickname() ?: memory.getUserName()
        val address = if (name != null) ", $name" else ", Sir"

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeGreet = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            hour < 21 -> "Good evening"
            else       -> "Welcome back"
        }

        val sb = StringBuilder("$timeGreet$address.")

        // Battery level
        val batteryLevel = getBatteryLevel()
        if (batteryLevel >= 0) {
            when {
                batteryLevel <= 15 -> sb.append(" Battery is low at ${batteryLevel}%. Please charge soon.")
                batteryLevel == 100 -> sb.append(" Battery is fully charged.")
                else -> sb.append(" Battery is at ${batteryLevel} percent.")
            }
        }

        // Habit suggestion (most-used app)
        val topApps = memory.getTopApps(1)
        if (topApps.isNotEmpty()) {
            sb.append(" You usually open ${topApps.first().appName} around this time.")
        }

        return sb.toString()
    }

    private fun getBatteryLevel(): Int {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = registerReceiver(null, filter) ?: return -1
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }

    // ─── Clock ────────────────────────────────────────────────────────────
    private fun setupClock() {
        val timer = object : Runnable {
            override fun run() {
                val now = Calendar.getInstance()
                val timeFmt = SimpleDateFormat("hh:mm", Locale.getDefault())
                val amPm   = SimpleDateFormat("a", Locale.getDefault())
                val dateFmt = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
                tvTime.text = "${timeFmt.format(now.time)} ${amPm.format(now.time)}"
                tvDate.text = dateFmt.format(now.time)
                tvTime.postDelayed(this, 30_000)
            }
        }
        tvTime.post(timer)
    }

    // ─── Greeting text ────────────────────────────────────────────────────
    private fun setupGreeting() {
        lifecycleScope.launch {
            val name = memory.getNickname() ?: memory.getUserName()
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val timeGreet = when {
                hour < 12 -> "Good morning"
                hour < 17 -> "Good afternoon"
                hour < 21 -> "Good evening"
                else       -> "Welcome back"
            }
            val address = if (name != null) ", $name" else ", Sir"
            tvGreeting.text = "$timeGreet$address."

            val topApps = memory.getTopApps(1)
            tvStatusLine.text = if (topApps.isNotEmpty()) {
                "You often open ${topApps.first().appName} at this time."
            } else {
                "Everything is ready. How can I help?"
            }
        }
    }

    // ─── Favourite Apps ───────────────────────────────────────────────────
    private fun setupFavoriteApps() {
        lifecycleScope.launch {
            val topApps = memory.getTopApps(6)
            val pm = packageManager
            val appItems = if (topApps.isNotEmpty()) {
                topApps.mapNotNull { usage ->
                    try {
                        val info = pm.getApplicationInfo(usage.packageName, 0)
                        Pair(pm.getApplicationLabel(info).toString(), usage.packageName)
                    } catch (e: Exception) { null }
                }
            } else {
                listOf(
                    "com.whatsapp", "com.android.chrome",
                    "com.google.android.youtube", "com.instagram.android",
                    "com.twitter.android", "com.spotify.music"
                ).mapNotNull { pkg ->
                    try {
                        val info = pm.getApplicationInfo(pkg, 0)
                        Pair(pm.getApplicationLabel(info).toString(), pkg)
                    } catch (e: Exception) { null }
                }
            }

            rvFavoriteApps.layoutManager = GridLayoutManager(this@HomeActivity, 4)
            rvFavoriteApps.adapter = FavoriteAppsAdapter(appItems) { pkg ->
                val intent = pm.getLaunchIntentForPackage(pkg)
                intent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                intent?.let { startActivity(it) }
            }
        }
    }

    // ─── Navigation Buttons ───────────────────────────────────────────────
    private fun setupNavButtons() {
        findViewById<View>(R.id.btn_orb).setOnClickListener {
            startActivity(Intent(this, VoiceActivity::class.java))
        }
        findViewById<View>(R.id.btn_chat).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
        findViewById<View>(R.id.btn_memory).setOnClickListener {
            startActivity(Intent(this, MemoryActivity::class.java))
        }
        findViewById<View>(R.id.btn_contacts).setOnClickListener {
            startActivity(Intent(this, ContactsActivity::class.java))
        }
        findViewById<View>(R.id.btn_alarms).setOnClickListener {
            startActivity(Intent(this, AlarmsActivity::class.java))
        }
        findViewById<View>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onBackPressed() {
        // Launcher never exits on back
    }
}
