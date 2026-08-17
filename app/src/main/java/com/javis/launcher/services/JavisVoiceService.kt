package com.javis.launcher.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.javis.launcher.JavisApplication
import com.javis.launcher.R
import com.javis.launcher.engine.voice.SpeechRecognitionEngine
import com.javis.launcher.engine.voice.VoiceEngine

class JavisVoiceService : Service() {
    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        JavisApplication.instance.voiceEngine?.stopSpeaking()
        JavisApplication.instance.voiceEngine?.shutdown()
        speechEngine?.stopListening()
        speechEngine?.destroy()
        speechEngine = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private var speechEngine: SpeechRecognitionEngine? = null

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
