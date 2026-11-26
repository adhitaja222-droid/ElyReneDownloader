package com.tikigsaver.app.engine

import com.tikigsaver.app.model.Platform

object UrlUtils {
    private val urlRegex = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)

    fun extractSupportedUrl(rawInput: String): String? {
        val candidate = urlRegex.find(rawInput.trim())?.value ?: rawInput.trim()
        val cleaned = candidate.trimEnd('.', ',', ')', ']', '"', '\'')
        return when (detectPlatform(cleaned)) {
            Platform.TIKTOK, Platform.INSTAGRAM -> cleaned
            Platform.UNKNOWN -> null
        }
    }

    fun detectPlatform(url: String): Platform {
        val lower = url.lowercase()
        return when {
            lower.contains("tiktok.com") || lower.contains("douyin.com") -> Platform.TIKTOK
            lower.contains("instagram.com") || lower.contains("instagr.am") -> Platform.INSTAGRAM
            else -> Platform.UNKNOWN
        }
    }

    fun extractInstagramShortcode(url: String): String? {
        val regex = Regex("""instagram\.com/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
        return regex.find(url)?.groupValues?.getOrNull(1)
    }
}
