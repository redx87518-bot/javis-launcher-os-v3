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
        val barCount = 32
        val barWidth = w / (barCount * 2f)
        val maxBarHeight = cy * 0.9f
        val color = when (currentState) {
            VoiceState.LISTENING -> Color.rgb(220, 40, 40)
            VoiceState.SPEAKING -> Color.rgb(0, 220, 80)
            VoiceState.THINKING -> Color.rgb(255, 160, 0)
            else -> Color.rgb(100, 100, 140)
        }

        for (i in 0 until barCount) {
            val frac = i.toFloat() / barCount
            val waveVal = sin((frac * 4 * Math.PI + phase).toFloat()).toFloat()
            val barH = (abs(waveVal) * amplitude * maxBarHeight).coerceAtLeast(4f)
            val x = frac * w + barWidth / 2
            val alpha = (180 + (waveVal * 75).toInt()).coerceIn(60, 255)
            paint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawRoundRect(
                x - barWidth / 2, cy - barH,
                x + barWidth / 2, cy + barH,
                barWidth / 2, barWidth / 2, paint
            )
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        waveAnimator.cancel()
    }
}
