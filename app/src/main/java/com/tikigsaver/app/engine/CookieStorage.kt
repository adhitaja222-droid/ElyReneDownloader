package com.tikigsaver.app.engine

import android.content.Context
import java.io.File

/**
 * Manages optional Instagram Netscape cookies.txt stored inside app private files
 * so yt-dlp can access HD 60fps Reels and multi-photo carousels without rate-limits.
 */
class CookieStorage(context: Context) {
    private val cookieFile = File(context.filesDir, "instagram_cookies.txt")

    fun hasInstagramCookies(): Boolean = cookieFile.exists() && cookieFile.length() > 20L

    fun getCookieFilePath(): String? = if (hasInstagramCookies()) cookieFile.absolutePath else null

    fun saveNetscapeCookies(netscapeContent: String) {
        cookieFile.writeText(netscapeContent)
    }

    fun saveFromWebViewCookieHeader(cookieHeader: String) {
        val lines = buildString {
            appendLine("# Netscape HTTP Cookie File")
            cookieHeader.split(";").map { it.trim() }.filter { it.contains("=") }.forEach { pair ->
                val idx = pair.indexOf('=')
                val name = pair.substring(0, idx).trim()
                val value = pair.substring(idx + 1).trim()
                if (name.isNotEmpty()) {
                    appendLine(".instagram.com\tTRUE\t/\tTRUE\t0\t$name\t$value")
                }
            }
        }
        saveNetscapeCookies(lines)
    }

    fun clearCookies() {
        if (cookieFile.exists()) {
            cookieFile.delete()
        }
    }
}
