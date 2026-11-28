package com.tikigsaver.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Crop
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
     * Mengoptimalkan video agar saat dikirim ke Status WhatsApp (Android) tetap tajam HD & tidak pecah:
     * - Jika ukuran <= 14.5 MB & resolusi <= 1080p: kirim langsung TANPA re-encode ulang (zero loss!).
     * - Jika > 14.5 MB: sesuaikan ukuran di ~13.8 MB (3.5–5.8 Mbps) agar tidak memicu kompresor brutal >16MB WhatsApp.
     */
    @Suppress("UnsafeOptInUsageError")
    suspend fun prepareWhatsAppHdStatus(
        context: Context,
        inputFile: File,
        outputFile: File
    ): File = withContext(Dispatchers.Main) {
        val (needReencode, targetHeight, targetBitrate) = withContext(Dispatchers.IO) {
            runCatching {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(inputFile.absolutePath)
                val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 15_000L
                mmr.release()

                val dispW = if (rot == 90 || rot == 270) h else w
                val dispH = if (rot == 90 || rot == 270) w else h
                val shortSide = minOf(dispW, dispH)
                val fileSize = inputFile.length()

                if (fileSize in 1L..14_500_000L && shortSide <= 1080) {
                    Triple(false, 0, 0)
                } else {
                    val durSec = (durMs / 1000.0).coerceAtLeast(3.0)
                    val maxShort = if (durSec <= 22.0) 1080 else 720
                    val outH = if (shortSide > maxShort && dispH > 0) {
                        val scale = maxShort.toFloat() / shortSide.toFloat()
                        (((dispH * scale) / 2f).roundToInt() * 2).coerceAtLeast(720)
                    } else {
                        0
                    }
                    val maxBySize = ((13_800_000.0 * 8.0) / durSec).toInt()
                    val br = maxBySize.coerceIn(3_500_000, 5_800_000)
                    Triple(true, outH, br)
                }
            }.getOrDefault(Triple(false, 0, 0))
        }

        if (!needReencode) {
            return@withContext inputFile
        }

        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val mediaItem = MediaItem.fromUri(Uri.fromFile(inputFile))
        val effects = if (targetHeight > 0) {
            listOf<Effect>(Presentation.createForHeight(targetHeight))
        } else {
            emptyList()
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
