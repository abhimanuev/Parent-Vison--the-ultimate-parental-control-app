package com.example.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object OverlayDialogManager {

    private var activeOverlayView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun showOverlayWarning(
        context: Context,
        packageName: String,
        appName: String,
        warningMessage: String,
        onDismiss: () -> Unit
    ) {
        if (!Settings.canDrawOverlays(context)) {
            return
        }

        mainHandler.post {
            removeCurrentOverlay(context)

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return@post

            val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutParamsType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            // Create root scrim
            val rootLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#B3000000")) // 70% black dim
                val pad = dpToPx(context, 24)
                setPadding(pad, pad, pad, pad)
            }

            // Create Card Container
            val cardLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                val cardPad = dpToPx(context, 22)
                setPadding(cardPad, cardPad, cardPad, cardPad)

                val shape = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E1B24")) // Dark surface
                    cornerRadius = dpToPx(context, 24).toFloat()
                    setStroke(dpToPx(context, 1), Color.parseColor("#3B3844"))
                }
                background = shape
            }

            // Title
            val titleView = TextView(context).apply {
                text = "$appName: Time's Up"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            cardLayout.addView(titleView)

            // Subtitle Tag
            val subTagView = TextView(context).apply {
                text = "Screen Time Limit Enforced"
                setTextColor(Color.parseColor("#CCC2DC"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                val subPad = dpToPx(context, 6)
                setPadding(0, subPad, 0, subPad * 2)
            }
            cardLayout.addView(subTagView)

            // Message box
            val msgBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val boxPad = dpToPx(context, 16)
                setPadding(boxPad, boxPad, boxPad, boxPad)
                val msgShape = GradientDrawable().apply {
                    setColor(Color.parseColor("#2A2633"))
                    cornerRadius = dpToPx(context, 14).toFloat()
                }
                background = msgShape
                val layoutMargin = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dpToPx(context, 8), 0, dpToPx(context, 18))
                }
                this.layoutParams = layoutMargin
            }

            val msgView = TextView(context).apply {
                text = warningMessage
                setTextColor(Color.parseColor("#E6E1E5"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.25f)
            }
            msgBox.addView(msgView)
            cardLayout.addView(msgBox)

            // OK / Return Home Button
            val returnButton = Button(context).apply {
                text = "Return to Home"
                setTextColor(Color.WHITE)
                val btnShape = GradientDrawable().apply {
                    setColor(Color.parseColor("#6750A4")) // M3 Primary
                    cornerRadius = dpToPx(context, 14).toFloat()
                }
                background = btnShape
                val btnParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(context, 48)
                )
                this.layoutParams = btnParams

                setOnClickListener {
                    removeCurrentOverlay(context)
                    returnHomeAndKill(context, packageName)
                    onDismiss()
                }
            }
            cardLayout.addView(returnButton)

            rootLayout.addView(cardLayout)

            try {
                windowManager.addView(rootLayout, params)
                activeOverlayView = rootLayout

                // Auto-dismiss safety timer (10 seconds)
                mainHandler.postDelayed({
                    removeCurrentOverlay(context)
                    returnHomeAndKill(context, packageName)
                    onDismiss()
                }, 10000L)
            } catch (_: Exception) {
            }
        }
    }

    fun removeCurrentOverlay(context: Context) {
        activeOverlayView?.let { view ->
            try {
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
            activeOverlayView = null
        }
    }

    private fun returnHomeAndKill(context: Context, packageName: String) {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)

            if (packageName.isNotBlank()) {
                mainHandler.postDelayed({
                    try {
                        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                        am?.killBackgroundProcesses(packageName)
                    } catch (_: Exception) {
                    }
                }, 400)
            }
        } catch (_: Exception) {
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
