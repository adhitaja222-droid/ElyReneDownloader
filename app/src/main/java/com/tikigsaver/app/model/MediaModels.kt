package com.tikigsaver.app.model

import android.net.Uri
import kotlin.math.roundToInt

enum class Platform(val label: String) {
    TIKTOK("TikTok"),
    INSTAGRAM("Instagram"),
    UNKNOWN("Lainnya")
}

enum class DownloadKind(val label: String) {
    VIDEO_2K("Video 2K"),
    VIDEO_HD("Video HD"),
    VIDEO_NORMAL("Video Normal"),
    MP3("Audio MP3"),
    IMAGE("Foto")
}

data class VideoQualityOption(
    val label: String,
    val formatSpec: String,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Double = 0.0,
    val fileSizeBytes: Long = 0L,
    val directFallbackUrl: String? = null,
    val isHd: Boolean = true,
    val upscaleTargetShort: Int = 0,
    val isEnhanced: Boolean = false
) {
    val resolutionSummary: String
        get() {
            val resPart = when {
                width > 0 && height > 0 -> "${minOf(width, height)}p (${width}×${height})"
                height > 0 -> "${height}p"
                else -> if (isHd) "Resolusi Tinggi" else "Standar"
            }
            val fpsPart = if (fps > 0.0) " • ${fps.roundToInt()} FPS" else ""
            val suffix = when {
                isEnhanced -> " • HD Upscale (FPS Asli Tetap)"
                fileSizeBytes > 0L -> " • ${formatBytes(fileSizeBytes)}"
                else -> ""
            }
            return "$resPart$fpsPart$suffix"
        }
}

data class PhotoSlideItem(
    val index: Int,
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
    val isVideo: Boolean = false,
    val videoUrl: String? = null
)

data class MediaContent(
    val sourceUrl: String,
    val platform: Platform,
    val title: String,
    val author: String = "",
    val thumbnailUrl: String? = null,
    val durationSeconds: Int = 0,
    val hdVideo: VideoQualityOption? = null,
    val normalVideo: VideoQualityOption? = null,
    val ultraVideo: VideoQualityOption? = null,
    val hasMp3: Boolean = true,
    val mp3DirectUrl: String? = null,
    val photos: List<PhotoSlideItem> = emptyList(),
    val extractorSource: String = "yt-dlp"
) {
    val hasVideo: Boolean get() = hdVideo != null || normalVideo != null || ultraVideo != null
    val hasPhotos: Boolean get() = photos.isNotEmpty()
}

data class SavedMediaRecord(
    val id: String,
    val title: String,
    val kind: DownloadKind,
    val platform: Platform,
    val fileUri: Uri,
    val fileName: String,
    val fileSizeBytes: Long,
    val verifiedSpecs: String? = null,
    val thumbnailUrl: String? = null,
    val sourceUrl: String = "",
    val vaultPath: String? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.0f KB", kb)
        else -> "$bytes B"
    }
}
