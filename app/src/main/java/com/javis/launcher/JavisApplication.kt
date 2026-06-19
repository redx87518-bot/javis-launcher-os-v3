package com.javis.launcher

import android.app.Application
import android.speech.tts.TextToSpeech
import com.javis.launcher.engine.memory.MemoryEngine
import com.javis.launcher.engine.voice.VoiceEngine

class JavisApplication : Application() {

    companion object {
        lateinit var instance: JavisApplication
            private set
    }

    lateinit var memoryEngine: MemoryEngine
    lateinit var voiceEngine: VoiceEngine

    override fun onCreate() {
        super.onCreate()
        instance = this
        memoryEngine = MemoryEngine(this)
        voiceEngine = VoiceEngine(this)
    }
}
