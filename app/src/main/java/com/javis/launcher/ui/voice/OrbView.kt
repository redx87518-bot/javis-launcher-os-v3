package com.javis.launcher.ui.voice

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.javis.launcher.models.VoiceState
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class OrbView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paintCore = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintRing = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintIndicator = Paint(Paint.ANTI_ALIAS_FLAG)

    private var pulseScale = 1f
    private var rotationAngle = 0f
    private var glowAlpha = 120
    private var currentState = VoiceState.IDLE

    private val pulseAnimator = ValueAnimator.ofFloat(0.9f, 1.1f).apply {
        duration = 1200; repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE; interpolator = LinearInterpolator()
        addUpdateListener { pulseScale = it.animatedValue as Float; invalidate() }
    }

    private val rotateAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 3000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { rotationAngle = it.animatedValue as Float; invalidate() }
    }

    private val glowAnimator = ValueAnimator.ofInt(60, 200).apply {
        duration = 800; repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE; interpolator = LinearInterpolator()
        addUpdateListener { glowAlpha = it.animatedValue as Int; invalidate() }
    }

    init {
        pulseAnimator.start()
    }

    fun setState(state: VoiceState) {
        currentState = state
        when (state) {
            VoiceState.IDLE -> {
                rotateAnimator.pause()
                glowAnimator.pause()
                pulseAnimator.start()
            }
            VoiceState.LISTENING -> {
                pulseAnimator.start()
                rotateAnimator.start()
                glowAnimator.start()
            }
            VoiceState.THINKING -> {
                pulseAnimator.pause()
                rotateAnimator.start()
                glowAnimator.start()
            }
            VoiceState.SPEAKING -> {
                rotateAnimator.start()
                glowAnimator.start()
                pulseAnimator.start()
            }
            VoiceState.EXECUTING -> {
                rotateAnimator.start()
                glowAnimator.start()
            }
            VoiceState.COMPLETED -> {
                rotateAnimator.pause()
                glowAnimator.pause()
                pulseAnimator.start()
            }
            VoiceState.ERROR -> {
                rotateAnimator.pause()
                glowAnimator.pause()
                pulseAnimator.start()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f * 0.75f

        // Outer glow
        val glowColor = stateGlowColor()
        paintGlow.color = Color.argb(glowAlpha / 3, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor))
        canvas.drawCircle(cx, cy, radius * pulseScale * 1.25f, paintGlow)

        // Energy ring
        paintRing.style = Paint.Style.STROKE
        paintRing.strokeWidth = 4f
        paintRing.color = glowColor
        paintRing.alpha = glowAlpha
        canvas.drawCircle(cx, cy, radius * pulseScale, paintRing)

        // Rotating dashes on ring
        if (currentState == VoiceState.LISTENING || currentState == VoiceState.THINKING || currentState == VoiceState.SPEAKING) {
            paintRing.strokeWidth = 6f
            paintRing.alpha = 255
            for (i in 0 until 8) {
                val angle = Math.toRadians((rotationAngle + i * 45.0))
                val x1 = cx + (radius - 10) * cos(angle).toFloat()
                val y1 = cy + (radius - 10) * sin(angle).toFloat()
                val x2 = cx + (radius + 10) * cos(angle).toFloat()
                val y2 = cy + (radius + 10) * sin(angle).toFloat()
                canvas.drawLine(x1, y1, x2, y2, paintRing)
            }
        }

        // Core circle
        val coreGradient = RadialGradient(cx, cy, radius * 0.7f * pulseScale,
            intArrayOf(Color.WHITE, Color.argb(220, 200, 220, 255), Color.argb(180, 30, 30, 60)),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        paintCore.shader = coreGradient
        canvas.drawCircle(cx, cy, radius * 0.7f * pulseScale, paintCore)

        // State indicators (green dots)
        paintIndicator.color = stateIndicatorColor()
        paintIndicator.alpha = 220
        for (i in 0 until 4) {
            val angle = Math.toRadians((rotationAngle * 0.5 + i * 90.0))
            val x = cx + radius * 0.5f * cos(angle).toFloat()
            val y = cy + radius * 0.5f * sin(angle).toFloat()
            canvas.drawCircle(x, y, 4f, paintIndicator)
        }
    }

    private fun stateGlowColor() = when (currentState) {
        VoiceState.IDLE -> Color.rgb(180, 0, 0)
        VoiceState.LISTENING -> Color.rgb(220, 20, 20)
        VoiceState.THINKING -> Color.rgb(255, 140, 0)
        VoiceState.SPEAKING -> Color.rgb(0, 200, 80)
        VoiceState.EXECUTING -> Color.rgb(0, 150, 255)
        VoiceState.COMPLETED -> Color.rgb(0, 220, 100)
        VoiceState.ERROR -> Color.rgb(255, 50, 50)
    }

    private fun stateIndicatorColor() = when (currentState) {
        VoiceState.IDLE -> Color.rgb(0, 180, 60)
        VoiceState.LISTENING -> Color.rgb(0, 220, 80)
        VoiceState.THINKING -> Color.rgb(255, 200, 0)
        VoiceState.SPEAKING -> Color.rgb(0, 255, 100)
        VoiceState.EXECUTING -> Color.rgb(0, 180, 255)
        VoiceState.COMPLETED -> Color.rgb(0, 255, 120)
        VoiceState.ERROR -> Color.rgb(255, 80, 80)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator.cancel(); rotateAnimator.cancel(); glowAnimator.cancel()
    }
}
