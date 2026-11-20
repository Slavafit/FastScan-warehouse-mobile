package com.ocr.fastscan.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import com.google.android.material.button.MaterialButton
import com.ocr.fastscan.R

class AnimatedSendButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle
) : MaterialButton(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val borderPath = Path()
    private val borderRect = RectF()
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = context.getColor(R.color.send_button_halo_base)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3f * density
        color = context.getColor(R.color.send_button_halo)
    }
    private val stateReset = Runnable { showIdle() }
    private var visualState = VisualState.IDLE
    private var animationProgress = 0f
    private var idleText: CharSequence? = null

    private val borderAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 900L
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            animationProgress = it.animatedValue as Float
            invalidate()
        }
    }

    fun showSending() {
        removeCallbacks(stateReset)
        if (idleText == null) idleText = text
        visualState = VisualState.SENDING
        text = context.getText(R.string.sending)
        isEnabled = false
        if (!borderAnimator.isStarted) borderAnimator.start()
        announceForAccessibility(text)
        invalidate()
    }

    fun showSuccess() {
        borderAnimator.cancel()
        visualState = VisualState.SUCCESS
        text = context.getText(R.string.sent_short)
        isEnabled = false
        announceForAccessibility(text)
        invalidate()
        postDelayed(stateReset, SUCCESS_DURATION_MS)
    }

    fun showError() {
        borderAnimator.cancel()
        visualState = VisualState.ERROR
        text = idleText ?: context.getText(R.string.send_order)
        isEnabled = false
        invalidate()
        postDelayed(stateReset, ERROR_DURATION_MS)
    }

    private fun showIdle() {
        borderAnimator.cancel()
        visualState = VisualState.IDLE
        text = idleText ?: context.getText(R.string.send_order)
        isEnabled = true
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val inset = basePaint.strokeWidth / 2f + density
        borderRect.set(inset, inset, width - inset, height - inset)
        borderPath.reset()
        val cornerRadius = resources.getDimension(R.dimen.control_corner_radius)
        borderPath.addRoundRect(borderRect, cornerRadius, cornerRadius, Path.Direction.CW)
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        when (visualState) {
            VisualState.IDLE -> Unit
            VisualState.SENDING -> {
                canvas.drawPath(borderPath, basePaint)
                val dashLength = 34f * density
                val gapLength = 18f * density
                accentPaint.pathEffect = DashPathEffect(
                    floatArrayOf(dashLength, gapLength),
                    -animationProgress * (dashLength + gapLength)
                )
                accentPaint.color = context.getColor(R.color.send_button_halo)
                canvas.drawPath(borderPath, accentPaint)
            }
            VisualState.SUCCESS -> {
                accentPaint.pathEffect = null
                accentPaint.color = context.getColor(R.color.send_button_halo)
                canvas.drawPath(borderPath, accentPaint)
            }
            VisualState.ERROR -> {
                accentPaint.pathEffect = null
                accentPaint.color = context.getColor(R.color.send_button_error)
                canvas.drawPath(borderPath, accentPaint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(stateReset)
        borderAnimator.cancel()
        super.onDetachedFromWindow()
    }

    private enum class VisualState { IDLE, SENDING, SUCCESS, ERROR }

    private companion object {
        const val SUCCESS_DURATION_MS = 650L
        const val ERROR_DURATION_MS = 900L
    }
}
