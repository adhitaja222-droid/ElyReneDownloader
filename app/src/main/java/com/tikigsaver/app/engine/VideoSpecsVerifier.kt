package com.tikigsaver.app.engine

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import java.io.File
import kotlin.math.roundToInt

object VideoSpecsVerifier {

    /**
     * Reads the actual width, height, and frame rate of the saved video file on-device
     * to verify that 60 FPS / HD resolution was preserved.
     */
    fun inspectVideoFile(file: File): String? {
        if (!file.exists()) return null
        var width = 0
        var height = 0
        var fps = 0.0

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val captureFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toDoubleOrNull()
                if (captureFps != null && captureFps > 0.0) {
                    fps = captureFps
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        if (fps <= 0.0 || width <= 0 || height <= 0) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("video/")) {
                        if (width <= 0 && format.containsKey(MediaFormat.KEY_WIDTH)) {
                            width = format.getInteger(MediaFormat.KEY_WIDTH)
                        }
                        if (height <= 0 && format.containsKey(MediaFormat.KEY_HEIGHT)) {
                            height = format.getInteger(MediaFormat.KEY_HEIGHT)
                        }
                        if (fps <= 0.0 && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                            fps = format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
                        }
                        if (fps <= 0.0) {
                            fps = estimateFpsFromSamples(extractor, i)
                        }
                        break
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    extractor.release()
                } catch (_: Exception) {
                }
            }
        }

        return when {
            width > 0 && height > 0 && fps > 0.0 -> "${width}×${height} • ${fps.roundToInt()} FPS"
            width > 0 && height > 0 -> "${width}×${height}"
            else -> null
        }
    }

    private fun estimateFpsFromSamples(extractor: MediaExtractor, trackIndex: Int): Double {
        extractor.selectTrack(trackIndex)
        var count = 0
        var firstTs = -1L
        var lastTs = -1L
        while (count < 120) {
            val ts = extractor.sampleTime
            if (ts < 0) break
            if (firstTs < 0) firstTs = ts
            lastTs = ts
            count++
            if (!extractor.advance()) break
        }
        val durationSec = (lastTs - firstTs) / 1_000_000.0
        return if (count > 2 && durationSec > 0.05) (count - 1) / durationSec else 0.0
    }
}
