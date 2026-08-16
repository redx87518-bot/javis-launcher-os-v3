package com.javis.launcher

import android.app.Application
import android.speech.tts.TextToSpeech
import android.util.Log
import com.javis.launcher.engine.memory.MemoryEngine
import com.javis.launcher.engine.voice.VoiceEngine

class JavisApplication : Application() {

    companion object {
        lateinit var instance: JavisApplication
            private set
    }

    var memoryEngine: MemoryEngine? = null
        private set
    var voiceEngine: VoiceEngine? = null
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            memoryEngine = MemoryEngine(this)
            voiceEngine = VoiceEngine(this)
        } catch (e: Exception) {
            Log.e("JavisApplication", "Failed to initialize engines", e)
        }
    }
}
