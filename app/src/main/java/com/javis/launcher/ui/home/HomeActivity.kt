package com.javis.launcher.ui.home

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.CallLog
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.javis.launcher.JavisApplication
import com.javis.launcher.R
import com.javis.launcher.engine.PersonalityEngine
import com.javis.launcher.receivers.UnlockReceiver
import com.javis.launcher.ui.alarms.AlarmsActivity
import com.javis.launcher.ui.chat.ChatActivity
import com.javis.launcher.ui.command.CommandCenterActivity
import com.javis.launcher.ui.contacts.ContactsActivity
import com.javis.launcher.ui.memory.MemoryActivity
import com.javis.launcher.ui.settings.SettingsActivity
import com.javis.launcher.ui.voice.VoiceActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : AppCompatActivity() {

    private val memory get() = JavisApplication.instance.memoryEngine
    private val voice  get() = JavisApplication.instance.voiceEngine

    private lateinit var tvGreeting:     TextView
    private lateinit var tvTime:         TextView
    private lateinit var tvDate:         TextView
    private lateinit var tvStatusLine:   TextView
    private lateinit var rvFavoriteApps: RecyclerView
    private lateinit var tvProviderStatus: TextView

    private val unlockReceiver = UnlockReceiver()
    private var receiverRegistered = false
    private var lastGreetedAtMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        tvGreeting      = findViewById(R.id.tv_greeting)
        tvTime          = findViewById(R.id.tv_time)
        tvDate          = findViewById(R.id.tv_date)
        tvStatusLine    = findViewById(R.id.tv_status_line)
        rvFavoriteApps  = findViewById(R.id.rv_favorite_apps)
        tvProviderStatus = findViewById(R.id.tv_provider_status)

        setupClock()
        setupGreeting()
        setupFavoriteApps()
        setupNavButtons()
        updateProviderBadge()
    }

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
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
        updateProviderBadge()
        maybeDeliverUnlockGreeting()
    }

    // ─── Unlock greeting (V4: PersonalityEngine) ──────────────────────────
    private fun maybeDeliverUnlockGreeting() {
        val now = System.currentTimeMillis()
        if (now - lastGreetedAtMs < 2 * 60 * 1000L) return
        if (!UnlockReceiver.consumePendingGreeting(this)) return
        lastGreetedAtMs = now

        lifecycleScope.launch {
            delay(600)
            val greeting = buildUnlockGreeting()
            tvStatusLine.text = greeting
            if (voice.isReady()) voice.speak(greeting)
        }
    }

    private suspend fun buildUnlockGreeting(): String = withContext(Dispatchers.IO) {
        val name       = memory.getNickname() ?: memory.getUserName()
        val battery    = getBatteryLevel()
        val missed     = getMissedCallCount()
        val unreadSms  = getUnreadSmsCount()
        PersonalityEngine.welcomeMessage(name, battery, missed, unreadSms)
    }

    // ─── Provider status badge ─────────────────────────────────────────────
    private fun updateProviderBadge() {
        val ai = com.javis.launcher.engine.ai.AIEngine(this)
        val provider = ai.getActiveProvider()
        tvProviderStatus.text = if (provider != null) "AI: ${provider.name}" else "AI: Not configured"
    }

    // ─── Clock ────────────────────────────────────────────────────────────
    private fun setupClock() {
        val timer = object : Runnable {
            override fun run() {
                val now     = Calendar.getInstance()
                val timeFmt = SimpleDateFormat("hh:mm", Locale.getDefault())
                val amPm    = SimpleDateFormat("a",     Locale.getDefault())
                val dateFmt = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
                tvTime.text = "${timeFmt.format(now.time)} ${amPm.format(now.time)}"
                tvDate.text = dateFmt.format(now.time)
                tvTime.postDelayed(this, 30_000)
            }
        }
        tvTime.post(timer)
    }

    // ─── Greeting text ───────────────────────────────────────────────────
    private fun setupGreeting() {
        lifecycleScope.launch {
            val name = memory.getNickname() ?: memory.getUserName()
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val greet = when {
                hour < 12 -> "Good morning"
                hour < 17 -> "Good afternoon"
                hour < 21 -> "Good evening"
                else      -> "Welcome back"
            }
            val address = if (name != null) ", $name" else when (PersonalityEngine.currentMode) {
                PersonalityEngine.Mode.JARVIS, PersonalityEngine.Mode.PROFESSIONAL -> ", Sir"
                PersonalityEngine.Mode.FRIENDLY -> ""
            }
            tvGreeting.text = "$greet$address."

            val topApps = memory.getTopApps(1)
            tvStatusLine.text = if (topApps.isNotEmpty()) {
                "You often open ${topApps.first().appName} around this time."
            } else {
                "All systems operational. How can I help?"
            }
        }
    }

    // ─── Favourite apps grid ──────────────────────────────────────────────
    private fun setupFavoriteApps() {
        lifecycleScope.launch {
            val topApps = memory.getTopApps(8)
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
                pm.getLaunchIntentForPackage(pkg)?.also {
                    it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(it)
                    memory.trackAppOpen(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString())
                }
            }
        }
    }

    // ─── Navigation ──────────────────────────────────────────────────────
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
        // V4: Command Center log button
        findViewById<View>(R.id.btn_command_center).setOnClickListener {
            startActivity(Intent(this, CommandCenterActivity::class.java))
        }
    }

    override fun onBackPressed() { /* Launcher never exits on back */ }

    // ─── System helpers ───────────────────────────────────────────────────
    private fun getBatteryLevel(): Int {
        return try {
            val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return -1
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) level * 100 / scale else -1
        } catch (e: Exception) { -1 }
    }

    private fun getMissedCallCount(): Int {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) return 0
        return try {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.IS_READ} = ?",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString(), "0"), null
            )
            val count = cursor?.count ?: 0
            cursor?.close()
            count
        } catch (e: Exception) { 0 }
    }

    private fun getUnreadSmsCount(): Int {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) return 0
        return try {
            val cursor = contentResolver.query(
                Uri.parse("content://sms/inbox"), arrayOf("_id"), "read = 0", null, null
            )
            val count = cursor?.count ?: 0
            cursor?.close()
            count
        } catch (e: Exception) { 0 }
    }
}
