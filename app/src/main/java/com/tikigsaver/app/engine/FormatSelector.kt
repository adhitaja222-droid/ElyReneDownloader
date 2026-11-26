package com.tikigsaver.app.engine

import com.tikigsaver.app.model.VideoQualityOption
import kotlin.math.roundToInt

data class ParsedFormat(
    val formatId: String,
    val ext: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val fps: Double = 0.0,
    val tbr: Double = 0.0,
    val abr: Double = 0.0,
    val vcodec: String = "",
    val acodec: String = "",
    val fileSize: Long = 0L,
    val formatNote: String = "",
    val url: String = ""
)

/**
 * Selects Normal, HD (1080p/original or +1 Tier Enhanced), and 2K (1440p) options
 * while preserving the highest available native FPS (e.g., 60fps stays 60fps).
 */
object FormatSelector {

    data class SelectedVideoOptions(
        val hd: VideoQualityOption,
        val normal: VideoQualityOption,
        val ultra: VideoQualityOption? = null
    )

    fun scaleDimensions(width: Int, height: Int, targetShort: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) {
            val h = ((targetShort * 16.0) / 9.0).roundToInt()
            return targetShort to (h + (h % 2))
        }
        return if (width <= height) {
            val newH = (height * (targetShort.toDouble() / width)).roundToInt()
            targetShort to (newH + (newH % 2))
        } else {
            val newW = (width * (targetShort.toDouble() / height)).roundToInt()
            (newW + (newW % 2)) to targetShort
        }
    }

    fun selectVideoOptions(formats: List<ParsedFormat>): SelectedVideoOptions {
        val videoStreams = formats.filter { fmt ->
            val vcodec = fmt.vcodec.lowercase()
            val hasVideoCodec = vcodec.isNotEmpty() && vcodec != "none"
            val hasDimensions = fmt.width > 0 || fmt.height > 0
            (hasVideoCodec || hasDimensions) && !isWatermarkFormat(fmt)
        }.ifEmpty {
            formats.filter { fmt ->
                val vcodec = fmt.vcodec.lowercase()
                vcodec != "none" && (fmt.width > 0 || fmt.height > 0)
            }
        }

        val audioStreams = formats.filter { fmt ->
            val acodec = fmt.acodec.lowercase()
            val vcodec = fmt.vcodec.lowercase()
            acodec.isNotEmpty() && acodec != "none" && (vcodec.isEmpty() || vcodec == "none")
        }

        val bestAudio = audioStreams.maxWithOrNull(
            compareBy<ParsedFormat> { if (it.ext.equals("m4a", ignoreCase = true)) 1 else 0 }
                .thenBy { it.abr }
                .thenBy { it.tbr }
        )

        val bestStream = videoStreams.maxWithOrNull(
            compareBy<ParsedFormat> { it.width.toLong() * it.height.toLong() }
                .thenBy { maxOf(it.width, it.height) }
                .thenBy { it.fps.roundToInt() }
                .thenBy { it.tbr }
                .thenBy { if (it.ext.equals("mp4", ignoreCase = true)) 1 else 0 }
        )

        fun buildOption(
            stream: ParsedFormat?,
            isHd: Boolean,
            upscaleShort: Int = 0,
            customLabel: String = ""
        ): VideoQualityOption {
            if (stream == null || stream.formatId.isBlank()) {
                val (w, h) = if (upscaleShort > 0) scaleDimensions(720, 1280, upscaleShort) else (0 to 0)
                return VideoQualityOption(
                    label = customLabel.ifBlank { if (isHd) "Download Video HD" else "Download Video Normal" },
                    formatSpec = if (isHd) "bestvideo+bestaudio/best" else "bestvideo[height<=720]+bestaudio/best[height<=720]/best",
                    width = w,
                    height = h,
                    isHd = isHd,
                    upscaleTargetShort = upscaleShort,
                    isEnhanced = upscaleShort > 0
                )
            }
            val isVideoOnly = stream.acodec.isBlank() || stream.acodec.equals("none", ignoreCase = true)
            val spec = if (isVideoOnly && bestAudio != null && bestAudio.formatId.isNotBlank()) {
                "${stream.formatId}+${bestAudio.formatId}/bestvideo+bestaudio/best"
            } else if (isVideoOnly) {
                "${stream.formatId}+bestaudio/bestvideo+bestaudio/best"
            } else {
                "${stream.formatId}/bestvideo+bestaudio/best"
            }
            val size = stream.fileSize + if (isVideoOnly) (bestAudio?.fileSize ?: 0L) else 0L
            val (outW, outH) = if (upscaleShort > 0) {
                scaleDimensions(stream.width, stream.height, upscaleShort)
            } else {
                stream.width to stream.height
            }

            return VideoQualityOption(
                label = customLabel.ifBlank { if (isHd) "Download Video HD" else "Download Video Normal" },
                formatSpec = spec,
                width = outW,
                height = outH,
                fps = stream.fps,
                fileSizeBytes = if (upscaleShort > 0) 0L else size,
                directFallbackUrl = if (!isVideoOnly && stream.url.isNotBlank()) stream.url else null,
                isHd = isHd,
                upscaleTargetShort = upscaleShort,
                isEnhanced = upscaleShort > 0
            )
        }

        val bestShort = bestStream?.let { minOf(it.width, it.height) } ?: 0

        val lowerCandidates = if (bestShort > 0) {
            videoStreams.filter { fmt ->
                val shortSide = minOf(fmt.width, fmt.height)
                shortSide in 1 until bestShort
            }
        } else {
            emptyList()
        }

        val normalStream = lowerCandidates.maxWithOrNull(
            compareBy<ParsedFormat> { minOf(it.width, it.height) }
                .thenBy { if (it.ext.equals("mp4", ignoreCase = true)) 1 else 0 }
                .thenBy { it.tbr }
        )

        val lightestStream = videoStreams.minWithOrNull(
            compareBy<ParsedFormat> { it.width.toLong() * it.height.toLong() }
                .thenBy { it.tbr }
                .thenBy { it.fileSize }
        )

        val normalOption = buildOption(
            stream = normalStream ?: lightestStream,
            isHd = false,
            upscaleShort = 0,
            customLabel = "Download Video"
        )

        val hdUpscale = if (bestShort in 1..720) 1080 else 0
        return SelectedVideoOptions(
            hd = buildOption(bestStream, true, hdUpscale, "Download Video HD"),
            normal = normalOption,
            ultra = null
        )
    }

    private fun isWatermarkFormat(fmt: ParsedFormat): Boolean {
        val id = fmt.formatId.lowercase()
        val note = fmt.formatNote.lowercase()
        return id.contains("watermark") ||
            (note.contains("watermark") && !note.contains("no watermark") && !note.contains("without watermark"))
    }
}
