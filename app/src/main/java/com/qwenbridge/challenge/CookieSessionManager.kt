package com.qwenbridge.challenge

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class CookieSessionManager : CookieJar {

    private val cookieManager: CookieManager = CookieManager.getInstance().apply {
        setAcceptCookie(true)
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        for (cookie in cookies) {
            cookieManager.setCookie(urlString, cookie.toString())
        }
        cookieManager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val urlString = url.toString()
        val cookieHeader = cookieManager.getCookie(urlString) ?: return emptyList()
        val result = mutableListOf<Cookie>()
        val pairs = cookieHeader.split(";")
        for (pair in pairs) {
            val trimmed = pair.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split("=", limit = 2)
            if (parts.size == 2) {
                val cookie = Cookie.Builder()
                    .name(parts[0].trim())
                    .value(parts[1].trim())
                    .domain(url.host)
                    .path("/")
                    .build()
                result.add(cookie)
            }
        }
        return result
    }

    fun getCookieString(url: String = "https://chat.qwen.ai"): String {
        return cookieManager.getCookie(url) ?: ""
    }

    fun setCookie(url: String, cookieString: String) {
        cookieManager.setCookie(url, cookieString)
        cookieManager.flush()
    }

    fun hasClearanceCookie(): Boolean {
        val cookies = getCookieString()
        return cookies.contains("cf_clearance") || cookies.contains("bx-") || cookies.contains("token=")
    }

    companion object {
        @Volatile
        private var INSTANCE: CookieSessionManager? = null

        fun getInstance(): CookieSessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CookieSessionManager().also { INSTANCE = it }
            }
        }
    }
}
