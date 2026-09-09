package com.qwenbridge.challenge

object ChallengeDetector {

    fun isChallenge(statusCode: Int, contentType: String?, bodySnippet: String): Boolean {
        if (statusCode == 401 || statusCode == 403 || statusCode == 502) {
            val lower = bodySnippet.lowercase()
            if (lower.contains("baxia") ||
                lower.contains("punish") ||
                lower.contains("rgv587") ||
                lower.contains("challenge") ||
                lower.contains("turnstile") ||
                lower.contains("cloudflare") ||
                lower.contains("cf-mitigated") ||
                lower.contains("captcha") ||
                lower.contains("sec_check") ||
                (contentType != null && contentType.contains("text/html"))) {
                return true
            }
        }
        return false
    }

    fun isFake200RateLimit(bodySnippet: String): Boolean {
        val lower = bodySnippet.lowercase()
        return lower.contains("rate limit") ||
                lower.contains("ratelimited") ||
                lower.contains("exceeded rate limit") ||
                lower.contains("too many requests") ||
                lower.contains("frequent requests") ||
                lower.contains("sıklıkla istek") ||
                lower.contains("kullanım sınırı")
    }
}
