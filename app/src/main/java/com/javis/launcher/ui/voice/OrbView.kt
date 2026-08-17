package com.javis.launcher.ui.voice

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.javis.launcher.models.VoiceState
import com.javis.launcher.util.ThemeManager
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
    private var themeColor = ThemeManager.orbColor(ThemeManager.getTheme(context))

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

        drawScanLines(canvas, cx, cy, radius)
        drawHolographicGlow(canvas, cx, cy, radius)
        drawEnergyRing(canvas, cx, cy, radius)
        drawRotatingDashes(canvas, cx, cy, radius)
        drawCore(canvas, cx, cy, radius)
        drawStateIndicator(canvas, cx, cy, radius)
    }

    private fun drawScanLines(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        scanPaint.style = Paint.Style.STROKE
        scanPaint.strokeWidth = 1f
        scanPaint.color = Color.argb(30, 255, 255, 255)

        val scanCount = 12
        for (i in 0 until scanCount) {
            val y = cy - radius + (i * radius * 2 / scanCount)
            val offset = (rotationAngle * 0.3f + i * 5) % (radius * 2)
            val alpha = (40 + 20 * sin((offset / radius * Math.PI).toFloat())).toInt().coerceIn(0, 80)
            scanPaint.color = Color.argb(alpha, 200, 220, 255)
            canvas.drawLine(cx - radius, y, cx + radius, y, scanPaint)
        }
    }

    private fun drawHolographicGlow(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val glowColor = themeColor
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        glowPaint.style = Paint.Style.FILL

        val outerGlow = RadialGradient(cx, cy, radius * 1.4f * pulseScale,
            intArrayOf(
                Color.argb(glowAlpha / 5, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                Color.argb(0, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor))
            ),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        glowPaint.shader = outerGlow
        canvas.drawCircle(cx, cy, radius * 1.4f * pulseScale, glowPaint)

        val midGlow = RadialGradient(cx, cy, radius * 1.15f * pulseScale,
            intArrayOf(
                Color.argb(glowAlpha / 4, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                Color.argb(0, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor))
            ),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        glowPaint.shader = midGlow
        canvas.drawCircle(cx, cy, radius * 1.15f * pulseScale, glowPaint)
    }

    private fun drawEnergyRing(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val ringColor = themeColor
        paintRing.style = Paint.Style.STROKE
        paintRing.strokeWidth = 3f
        paintRing.color = ringColor
        paintRing.alpha = glowAlpha / 2
        canvas.drawCircle(cx, cy, radius * pulseScale * 0.95f, paintRing)

        paintRing.strokeWidth = 1.5f
        paintRing.alpha = glowAlpha
        canvas.drawCircle(cx, cy, radius * pulseScale, paintRing)
    }

    private fun drawRotatingDashes(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        if (currentState == VoiceState.LISTENING || currentState == VoiceState.THINKING || currentState == VoiceState.SPEAKING) {
            paintRing.style = Paint.Style.STROKE
            paintRing.strokeWidth = 5f
            paintRing.alpha = 255

            for (i in 0 until 12) {
                val angle = Math.toRadians((rotationAngle + i * 30.0))
                val innerR = radius * 0.85f
                val outerR = radius * 1.05f
                val x1 = cx + innerR * cos(angle).toFloat()
                val y1 = cy + innerR * sin(angle).toFloat()
                val x2 = cx + outerR * cos(angle).toFloat()
                val y2 = cy + outerR * sin(angle).toFloat()

                val dashAlpha = if (i % 2 == 0) 255 else 120
                paintRing.color = themeColor
                paintRing.alpha = dashAlpha
                canvas.drawLine(x1, y1, x2, y2, paintRing)
            }
        }
    }

    private fun drawCore(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val coreGradient = RadialGradient(cx, cy, radius * 0.65f * pulseScale,
            intArrayOf(
                Color.WHITE,
                Color.argb(240, Color.red(themeColor), Color.green(themeColor), Color.blue(themeColor)),
                Color.argb(160, 20, 25, 50)
            ),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        paintCore.shader = coreGradient
        canvas.drawCircle(cx, cy, radius * 0.65f * pulseScale, paintCore)

        val innerGlow = RadialGradient(cx, cy, radius * 0.3f * pulseScale,
            intArrayOf(Color.WHITE, Color.argb(100, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        paintCore.shader = innerGlow
        canvas.drawCircle(cx, cy, radius * 0.3f * pulseScale, paintCore)
    }

    private fun drawStateIndicator(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        paintIndicator.color = stateIndicatorColor()
        paintIndicator.alpha = 200
        for (i in 0 until 6) {
            val angle = Math.toRadians((rotationAngle * 0.3 + i * 60.0))
            val x = cx + radius * 0.75f * cos(angle).toFloat()
            val y = cy + radius * 0.75f * sin(angle).toFloat()
            paintIndicator.alpha = if (i % 2 == 0) 220 else 100
            canvas.drawCircle(x, y, 3.5f, paintIndicator)
        }
    }

    private fun stateGlowColor() = when (currentState) {
        VoiceState.IDLE -> themeColor
        VoiceState.LISTENING -> themeColor
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
