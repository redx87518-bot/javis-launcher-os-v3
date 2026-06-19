package com.javis.launcher.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.javis.launcher.JavisApplication
import com.javis.launcher.R
import com.javis.launcher.ui.alarms.AlarmsActivity
import com.javis.launcher.ui.chat.ChatActivity
import com.javis.launcher.ui.contacts.ContactsActivity
import com.javis.launcher.ui.memory.MemoryActivity
import com.javis.launcher.ui.settings.SettingsActivity
import com.javis.launcher.ui.voice.VoiceActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : AppCompatActivity() {

    private val memory get() = JavisApplication.instance.memoryEngine

    private lateinit var tvGreeting: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvStatusLine: TextView
    private lateinit var rvFavoriteApps: RecyclerView
    private lateinit var tvProviderStatus: TextView

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

    private fun setupClock() {
        val timer = object : Runnable {
            override fun run() {
                val now = Calendar.getInstance()
                val timeFmt = SimpleDateFormat("hh:mm", Locale.getDefault())
                val amPm = SimpleDateFormat("a", Locale.getDefault())
                val dateFmt = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
                tvTime.text = "${timeFmt.format(now.time)} ${amPm.format(now.time)}"
                tvDate.text = dateFmt.format(now.time)
                tvTime.postDelayed(this, 30000)
            }
        }
        tvTime.post(timer)
    }

    private fun setupGreeting() {
        lifecycleScope.launch {
            val name = memory.getNickname() ?: memory.getUserName()
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val timeGreet = when {
                hour < 12 -> "Good morning"
                hour < 17 -> "Good afternoon"
                hour < 21 -> "Good evening"
                else -> "Welcome back"
            }
            val address = if (name != null) ", $name" else ", Sir"
            tvGreeting.text = "$timeGreet$address."

            // Status line
            val topApps = memory.getTopApps(1)
            tvStatusLine.text = if (topApps.isNotEmpty()) {
                "You often open ${topApps.first().appName} at this time."
            } else {
                "Everything is ready. How can I help?"
            }
        }
    }

    private fun setupFavoriteApps() {
        lifecycleScope.launch {
            val topApps = memory.getTopApps(6)
            val pm = packageManager
            val appItems = if (topApps.isNotEmpty()) {
                topApps.map { usage ->
                    try {
                        val info = pm.getApplicationInfo(usage.packageName, 0)
                        Pair(pm.getApplicationLabel(info).toString(), usage.packageName)
                    } catch (e: Exception) { null }
                }.filterNotNull()
            } else {
                // Default popular apps
                listOf("com.whatsapp", "com.android.chrome", "com.google.android.youtube",
                    "com.instagram.android", "com.twitter.android", "com.spotify.music")
                    .mapNotNull { pkg ->
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

    private fun setupNavButtons() {
        // Main AI Orb → Voice Mode
        findViewById<View>(R.id.btn_orb).setOnClickListener {
            startActivity(Intent(this, VoiceActivity::class.java))
        }

        // Chat button
        findViewById<View>(R.id.btn_chat).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        // Quick nav buttons
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

    override fun onResume() {
        super.onResume()
        setupGreeting()
        setupFavoriteApps()
    }

    override fun onBackPressed() {
        // Launcher doesn't exit on back
    }
}
