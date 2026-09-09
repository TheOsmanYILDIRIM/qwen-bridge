package com.qwenbridge.challenge

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.qwenbridge.data.ConfigManager
import kotlinx.coroutines.CompletableDeferred

class ChallengeOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val configManager = ConfigManager.getInstance(context)

    private var rootOverlayLayout: FrameLayout? = null
    private var webView: WebView? = null
    private var isExpanded = false
    private var currentChallengeDeferred: CompletableDeferred<Boolean>? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun initOverlay() {
        if (!Settings.canDrawOverlays(context)) return
        if (rootOverlayLayout != null) return

        mainHandler.post {
            try {
                val frameLayout = FrameLayout(context)

                val cardLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#1E293B"))
                        cornerRadius = 32f
                        setStroke(2, Color.parseColor("#6366F1"))
                    }
                    setPadding(24, 24, 24, 24)
                }

                val headerLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val titleText = TextView(context).apply {
                    text = "🛡️ Qwen Security Verification"
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val closeBtn = Button(context).apply {
                    text = "✕"
                    setTextColor(Color.LTGRAY)
                    setBackgroundColor(Color.TRANSPARENT)
                    setOnClickListener {
                        minimizeOverlay(solved = false)
                    }
                }

                headerLayout.addView(titleText)
                headerLayout.addView(closeBtn)
                cardLayout.addView(headerLayout)

                val wv = WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                    }
                    setBackgroundColor(Color.parseColor("#0F172A"))

                    addJavascriptInterface(ChallengeBridge(), "QwenChallengeBridge")

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val url = request?.url?.toString() ?: ""
                            if (url.contains("challenges.cloudflare.com") ||
                                url.contains("/cdn-cgi/challenge-platform/") ||
                                url.contains("baxia.js") ||
                                url.contains("sec_check")) {
                                mainHandler.post {
                                    if (!isExpanded) expandOverlay()
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            injectChallengeObserver()
                            checkCookiesAndResolution(url)
                        }
                    }
                }
                webView = wv

                val webViewParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (context.resources.displayMetrics.density * 360).toInt()
                )
                cardLayout.addView(wv, webViewParams)

                val statusText = TextView(context).apply {
                    text = "Please complete the verification on screen to proceed."
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 12f
                    setPadding(8, 12, 8, 4)
                }
                cardLayout.addView(statusText)

                frameLayout.addView(cardLayout)
                rootOverlayLayout = frameLayout

                val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                // Initial minimized state (1x1 pixel, unclickable)
                val params = WindowManager.LayoutParams(
                    1, 1,
                    windowType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 0
                    y = 0
                }

                windowManager.addView(frameLayout, params)
                frameLayout.visibility = View.GONE
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun triggerChallengeFlow(targetUrl: String = "https://chat.qwen.ai"): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        currentChallengeDeferred = deferred

        mainHandler.post {
            initOverlay()
            expandOverlay()
            webView?.loadUrl(targetUrl)
        }

        return deferred.await()
    }

    private fun expandOverlay() {
        if (isExpanded) return
        isExpanded = true
        configManager.setChallengeActive(true)

        mainHandler.post {
            val view = rootOverlayLayout ?: return@post
            val dm = context.resources.displayMetrics
            val width = (dm.widthPixels * 0.92).toInt().coerceAtMost((dm.density * 420).toInt())

            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            view.visibility = View.VISIBLE
            windowManager.updateViewLayout(view, params)
        }
    }

    private fun minimizeOverlay(solved: Boolean) {
        isExpanded = false
        configManager.setChallengeActive(false)
        if (solved) {
            configManager.setLastChallengeSolvedTime(System.currentTimeMillis())
        }

        mainHandler.post {
            val view = rootOverlayLayout ?: return@post
            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                1, 1,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            view.visibility = View.GONE
            windowManager.updateViewLayout(view, params)

            currentChallengeDeferred?.complete(solved)
            currentChallengeDeferred = null
        }
    }

    private fun injectChallengeObserver() {
        val js = """
            (function() {
                if (window.__qwenObserverInjected) return;
                window.__qwenObserverInjected = true;

                const checkChallengeStatus = () => {
                    const hasCfSuccess = document.querySelector('.cf-turnstile-feedback') || 
                                         (document.cookie && document.cookie.includes('cf_clearance'));
                    const hasBaxiaSolved = !document.querySelector('div#baxia-dialog') && 
                                           !document.querySelector('iframe[src*="punish"]');
                    
                    if (hasCfSuccess || (window.__baxiaTriggered && hasBaxiaSolved)) {
                        QwenChallengeBridge.onChallengeSolved();
                    }
                };

                const observer = new MutationObserver(() => checkChallengeStatus());
                observer.observe(document.body, { childList: true, subtree: true });
                setInterval(checkChallengeStatus, 1500);
            })();
        """.trimIndent()
        webView?.evaluateJavascript(js, null)
    }

    private fun checkCookiesAndResolution(url: String?) {
        val cookies = CookieManager.getInstance().getCookie(url ?: "https://chat.qwen.ai") ?: ""
        if (cookies.contains("cf_clearance") || cookies.contains("token=")) {
            CookieSessionManager.getInstance().setCookie("https://chat.qwen.ai", cookies)
            if (isExpanded) {
                mainHandler.postDelayed({
                    minimizeOverlay(solved = true)
                }, 800)
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            rootOverlayLayout?.let {
                windowManager.removeView(it)
            }
            webView?.destroy()
            rootOverlayLayout = null
            webView = null
        }
    }

    inner class ChallengeBridge {
        @JavascriptInterface
        fun onChallengeSolved() {
            mainHandler.post {
                checkCookiesAndResolution("https://chat.qwen.ai")
                minimizeOverlay(solved = true)
            }
        }

        @JavascriptInterface
        fun onChallengeAppeared() {
            mainHandler.post {
                expandOverlay()
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ChallengeOverlayManager? = null

        fun getInstance(context: Context): ChallengeOverlayManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChallengeOverlayManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
