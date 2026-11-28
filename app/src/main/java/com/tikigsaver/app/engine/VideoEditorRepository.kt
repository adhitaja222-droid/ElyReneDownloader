package com.tikigsaver.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Crop
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object VideoEditorRepository {

    suspend fun extractThumbFile(videoFile: File, outFile: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(videoFile.absolutePath)
            val bmp = mmr.getFrameAtTime(800_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: mmr.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            mmr.release()
            if (bmp != null) {
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                bmp.recycle()
                outFile.absolutePath
            } else null
        }.getOrNull()
    }

    suspend fun extractTimelineFrames(videoUri: Uri, context: Context, count: Int = 8): List<Bitmap> =
        withContext(Dispatchers.IO) {
            val frames = mutableListOf<Bitmap>()
            val mmr = MediaMetadataRetriever()
            runCatching {
                mmr.setDataSource(context, videoUri)
                val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 5000L
                val stepUs = (durMs.coerceAtLeast(1000L) * 1000L) / count
                for (i in 0 until count) {
                    val bmp = mmr.getFrameAtTime(i * stepUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (bmp != null) {
                        val scaled = Bitmap.createScaledBitmap(bmp, 96, 96, true)
                        if (scaled !== bmp) bmp.recycle()
                        frames.add(scaled)
                    }
                }
            }
            runCatching { mmr.release() }
            frames
        }

    suspend fun editVideo(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        startMs: Long,
        endMs: Long,
        cropX: Float?,
        cropY: Float?,
        cropW: Float?,
        cropH: Float?
    ): File = withContext(Dispatchers.Main) {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val clipBuilder = MediaItem.ClippingConfiguration.Builder()
        if (startMs > 0L) clipBuilder.setStartPositionMs(startMs)
        if (endMs > startMs) clipBuilder.setEndPositionMs(endMs)

        val mediaItem = MediaItem.Builder()
            .setUri(inputUri)
            .setClippingConfiguration(clipBuilder.build())
            .build()

        val videoEffects = mutableListOf<Effect>()
        if (cropX != null && cropY != null && cropW != null && cropH != null &&
            (cropW < 0.99f || cropH < 0.99f)
        ) {
            val cw = cropW.coerceIn(0.1f, 1f)
            val ch = cropH.coerceIn(0.1f, 1f)
            val cx = cropX.coerceIn(0f, 1f - cw)
            val cy = cropY.coerceIn(0f, 1f - ch)
            val left = (cx * 2f) - 1f
            val right = ((cx + cw) * 2f) - 1f
            val top = 1f - (cy * 2f)
            val bottom = 1f - ((cy + ch) * 2f)
            if (left < right && bottom < top) {
                videoEffects.add(Crop(left, right, bottom, top))
            }
        }

        val editedItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(15_000_000)
                    .build()
            )
            .build()

        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(outputFile)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                })
                .build()

            transformer.start(editedItem, outputFile.absolutePath)
            cont.invokeOnCancellation { runCatching { transformer.cancel() } }
        }
    }

    /**
     * Mengoptimalkan video agar sesuai persis dengan batas passthrough Status WhatsApp Android (ala Pure Status):
     * - Resolusi maksimal 720p (sisi terpendek 720px, tidak melebihi batas Status WA)
     * - Frame rate diturunkan ke 30 FPS (via FrameDropEffect) agar tiap frame mendapat 2x bitrate & tidak dibantai WA
     * - Bitrate optimal ~3.2 Mbps (ukuran selalu < 14 MB) + H.264 + AAC sehingga WA tidak melakukan kompresi ulang brutal.
     */
    @Suppress("UnsafeOptInUsageError")
    suspend fun prepareWhatsAppHdStatus(
        context: Context,
        inputFile: File,
        outputFile: File
    ): File = withContext(Dispatchers.Main) {
        val (needReencode, targetHeight, targetBitrate, need30Fps) = withContext(Dispatchers.IO) {
            runCatching {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(inputFile.absolutePath)
                val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 15_000L
                val fps = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 0f
                val brMeta = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
                mmr.release()

                val dispW = if (rot == 90 || rot == 270) h else w
                val dispH = if (rot == 90 || rot == 270) w else h
                val shortSide = minOf(dispW, dispH)
                val fileSize = inputFile.length()
                val durSec = (durMs / 1000.0).coerceAtLeast(3.0)
                val avgBitrate = if (brMeta > 0) brMeta else ((fileSize * 8.0) / durSec).toInt()
                val highFps = fps > 33f || avgBitrate > 4_500_000

                // Hanya skip re-encode jika video SUDAH <=720p, <=3.8 Mbps, <=12 MB, dan bukan 60fps
                if (fileSize in 1L..12_000_000L && shortSide in 1..720 && avgBitrate <= 3_800_000 && fps <= 33f) {
                    listOf(0, 0, 0, 0)
                } else {
                    val outH = if (shortSide > 720 && dispH > 0) {
                        val scale = 720f / shortSide.toFloat()
                        (((dispH * scale) / 16f).roundToInt() * 16).coerceAtLeast(360)
                    } else {
                        0
                    }
                    val maxBySize = ((13_000_000.0 * 8.0) / durSec).toInt()
                    val br = maxBySize.coerceIn(2_400_000, 3_400_000)
                    listOf(1, outH, br, if (highFps) 1 else 0)
                }
            }.getOrDefault(listOf(1, 0, 3_200_000, 1))
        }

        if (needReencode == 0) {
            return@withContext inputFile
        }

        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val mediaItem = MediaItem.fromUri(Uri.fromFile(inputFile))
        val effects = mutableListOf<Effect>()
        if (need30Fps == 1) {
            effects.add(FrameDropEffect.createDefaultFrameDropEffect(30f))
        }
        if (targetHeight > 0) {
            effects.add(Presentation.createForHeight(targetHeight))
        }
        val editedItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), effects))
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(targetBitrate)
                    .build()
            )
            .build()

        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(outputFile)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                })
                .build()

            transformer.start(editedItem, outputFile.absolutePath)
            cont.invokeOnCancellation { runCatching { transformer.cancel() } }
        }
    }
}
