package com.javis.launcher.ui.voice

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.javis.launcher.models.VoiceState
import kotlin.math.abs
import kotlin.math.sin

class VoiceWaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 4f
    }

    private var phase = 0f
    private var amplitude = 0.3f
    private var targetAmplitude = 0.3f
    private var currentState = VoiceState.IDLE

    private val waveAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
        duration = 800; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            amplitude += (targetAmplitude - amplitude) * 0.1f
            invalidate()
        }
    }

    init { waveAnimator.start() }

    fun setState(state: VoiceState) {
        currentState = state
        targetAmplitude = when (state) {
            VoiceState.IDLE -> 0.15f
            VoiceState.LISTENING -> 0.7f
            VoiceState.THINKING -> 0.4f
            VoiceState.SPEAKING -> 0.8f
            VoiceState.EXECUTING -> 0.5f
            VoiceState.COMPLETED -> 0.2f
            VoiceState.ERROR -> 0.1f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cy = h / 2f
        val barCount = 48
        val barWidth = w / (barCount * 2.5f)
        val maxBarHeight = cy * 0.85f

        val baseColor = when (currentState) {
            VoiceState.LISTENING -> Color.rgb(220, 60, 60)
            VoiceState.SPEAKING -> Color.rgb(0, 220, 100)
            VoiceState.THINKING -> Color.rgb(255, 180, 0)
            VoiceState.EXECUTING -> Color.rgb(0, 180, 255)
            else -> Color.rgb(100, 120, 160)
        }

        for (i in 0 until barCount) {
            val frac = i.toFloat() / barCount
            val wave1 = sin((frac * 6 * Math.PI + phase).toFloat()).toFloat()
            val wave2 = sin((frac * 3 * Math.PI - phase * 0.7).toFloat()).toFloat()
            val combined = (wave1 * 0.6f + wave2 * 0.4f)
            val barH = (abs(combined) * amplitude * maxBarHeight).coerceAtLeast(3f)
            val x = frac * w + barWidth / 2

            val alpha = (140 + (combined * 115).toInt()).coerceIn(50, 255)
            val r = (Color.red(baseColor) + (combined * 30).toInt()).coerceIn(0, 255)
            val g = (Color.green(baseColor) + (combined * 30).toInt()).coerceIn(0, 255)
            val b = (Color.blue(baseColor) + (combined * 30).toInt()).coerceIn(0, 255)
            paint.color = Color.argb(alpha, r, g, b)

            val gradient = LinearGradient(x, cy - barH, x, cy + barH,
                intArrayOf(paint.color, Color.argb(alpha / 2, r, g, b)),
                null, Shader.TileMode.CLAMP)
            paint.shader = gradient
            canvas.drawRoundRect(
                x - barWidth / 2, cy - barH,
                x + barWidth / 2, cy + barH,
                barWidth / 3, barWidth / 3, paint
            )
        }
        paint.shader = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        waveAnimator.cancel()
    }
}
