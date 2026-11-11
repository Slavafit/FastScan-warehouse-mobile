package com.ocr.fastscan.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import com.ocr.fastscan.R

object StatusToast {

    fun error(context: Context?, @StringRes messageRes: Int, duration: Int = Toast.LENGTH_SHORT) {
        val activeContext = context ?: return
        show(activeContext, activeContext.getString(messageRes), R.drawable.ic_toast_error, duration)
    }

    fun error(context: Context?, message: String, duration: Int = Toast.LENGTH_SHORT) {
        val activeContext = context ?: return
        show(activeContext, message, R.drawable.ic_toast_error, duration)
    }

    fun warning(context: Context?, @StringRes messageRes: Int, duration: Int = Toast.LENGTH_SHORT) {
        val activeContext = context ?: return
        show(activeContext, activeContext.getString(messageRes), R.drawable.ic_toast_warning, duration)
    }

    fun warning(context: Context?, message: String, duration: Int = Toast.LENGTH_SHORT) {
        val activeContext = context ?: return
        show(activeContext, message, R.drawable.ic_toast_warning, duration)
    }

    private fun show(context: Context, message: String, iconRes: Int, duration: Int) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { show(context, message, iconRes, duration) }
            return
        }

        val appContext = context.applicationContext
        val view = LayoutInflater.from(appContext).inflate(R.layout.toast_status, null)
        view.findViewById<ImageView>(R.id.ivStatusIcon).setImageResource(iconRes)
        view.findViewById<TextView>(R.id.tvStatusMessage).text = message

        @Suppress("DEPRECATION")
        Toast(appContext).apply {
            this.duration = duration
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dp(appContext, 88))
            this.view = view
        }.show()
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
