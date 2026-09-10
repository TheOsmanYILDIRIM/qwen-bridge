package com.qwenbridge.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import com.qwenbridge.MainActivity
import com.qwenbridge.data.ConfigManager
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs

class FloatingStatusOverlay private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val configManager = ConfigManager.getInstance(appContext)
    private val prefs = appContext.getSharedPreferences("qwen_bridge_prefs", Context.MODE_PRIVATE)
    private val touchSlop = ViewConfiguration.get(appContext).scaledTouchSlop

    private var root: FrameLayout? = null
    private var dot: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var probeRunnable: Runnable? = null
    private var serverUp = false

    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragActive = false

    fun show() {
        if (!Settings.canDrawOverlays(appContext)) return
        if (root != null) return
        mainHandler.post {
            if (root != null) return@post
            val density = appContext.resources.displayMetrics.density
            val pillWidth = (44 * density).toInt()
            val pillHeight = (26 * density).toInt()
            val dotSize = (10 * density).toInt()

            dot = View(appContext).apply {
                background = buildDotBackground(Color.parseColor("#F87171"))
                layoutParams = FrameLayout.LayoutParams(dotSize, dotSize).apply {
                    gravity = Gravity.CENTER
                }
            }

            val v = FrameLayout(appContext).apply {
                isClickable = true
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(0x5522233A)
                    cornerRadius = (13 * density)
                    setStroke((1 * density).toInt(), 0x556366F1)
                }
                addView(dot)
                setOnTouchListener(dragListener)
            }
            root = v

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val lp = WindowManager.LayoutParams(
                pillWidth,
                pillHeight,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = prefs.getInt(KEY_X, -1)
                y = prefs.getInt(KEY_Y, -1)
            }
            params = lp

            if (lp.x < 0 || lp.y < 0) {
                val dm = appContext.resources.displayMetrics
                lp.x = dm.widthPixels - (44 * density).toInt() - (16 * density).toInt()
                lp.y = dm.heightPixels - (26 * density).toInt() - (170 * density).toInt()
                savePosition(lp.x, lp.y)
            }

            windowManager.addView(v, lp)
            startProbe()
        }
    }

    fun destroy() {
        mainHandler.post {
            probeRunnable?.let { mainHandler.removeCallbacks(it) }
            probeRunnable = null
            root?.let { runCatching { windowManager.removeView(it) } }
            root = null
            dot = null
            params = null
            serverUp = false
        }
    }

    private fun startProbe() {
        val initialProbe = Runnable {
            val r = probeRunnable
            if (r == null) return@Runnable
            val port = configManager.config.value.port
            Thread {
                val up = probePort(port)
                mainHandler.post {
                    updateDot(up)
                    if (root != null) mainHandler.postDelayed(r, PROBE_INTERVAL_MS)
                }
            }.start()
        }
        probeRunnable = initialProbe
        mainHandler.post(initialProbe)
    }

    private fun probePort(port: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", port), PROBE_TIMEOUT_MS)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun updateDot(up: Boolean) {
        if (serverUp == up) return
        serverUp = up
        dot?.background = buildDotBackground(
            if (up) Color.parseColor("#22C55E") else Color.parseColor("#F87171")
        )
    }

    private fun buildDotBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun openDashboard() {
        try {
            val intent = Intent(appContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            appContext.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun savePosition(x: Int, y: Int) {
        prefs.edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply()
    }

    private val dragListener = View.OnTouchListener { view, event ->
        val p = params ?: return@OnTouchListener false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                dragStartX = p.x.toFloat()
                dragStartY = p.y.toFloat()
                dragActive = false
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragActive &&
                    (abs(event.rawX - dragStartRawX) > touchSlop || abs(event.rawY - dragStartRawY) > touchSlop)
                ) {
                    dragActive = true
                }
                if (dragActive) {
                    p.x = (dragStartX + (event.rawX - dragStartRawX)).toInt()
                    p.y = (dragStartY + (event.rawY - dragStartRawY)).toInt()
                    runCatching { windowManager.updateViewLayout(view, p) }
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragActive) openDashboard()
                savePosition(p.x, p.y)
                true
            }
            else -> false
        }
    }

    companion object {
        private const val PROBE_INTERVAL_MS = 2000L
        private const val PROBE_TIMEOUT_MS = 500
        private const val KEY_X = "overlay_pos_x"
        private const val KEY_Y = "overlay_pos_y"

        @Volatile
        private var INSTANCE: FloatingStatusOverlay? = null

        fun getInstance(context: Context): FloatingStatusOverlay {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FloatingStatusOverlay(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}