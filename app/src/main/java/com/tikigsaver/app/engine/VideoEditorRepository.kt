package com.tikigsaver.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
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

        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
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
     * Mengoptimalkan video agar saat dikirim ke Status WhatsApp (Android) tidak di-kompres
     * secara agresif / pecah (ala Pure Status): H.264 + AAC, max short-side 720p HD, bitrate ~4.5 Mbps.
     */
    @OptIn(UnstableApi::class)
    suspend fun prepareWhatsAppHdStatus(
        context: Context,
        inputFile: File,
        outputFile: File
    ): File = withContext(Dispatchers.Main) {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val mediaItem = MediaItem.fromUri(Uri.fromFile(inputFile))
        val effects = listOf<Effect>(Presentation.createForShortSide(720))
        val editedItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), effects))
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(4_500_000)
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
