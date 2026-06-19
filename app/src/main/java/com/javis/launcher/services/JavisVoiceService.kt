package com.javis.launcher.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.javis.launcher.R

class JavisVoiceService : Service() {
    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1, buildNotification())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val ch = NotificationChannel("javis_voice", "JAVIS Voice", NotificationManager.IMPORTANCE_LOW)
        ch.description = "JAVIS voice recognition service"
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, "javis_voice")
            .setContentTitle("JAVIS")
            .setContentText("Voice assistant active")
            .setSmallIcon(R.drawable.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
