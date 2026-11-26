package com.tikigsaver.app.engine

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.tikigsaver.app.model.DownloadKind
import com.tikigsaver.app.model.MediaContent
import com.tikigsaver.app.model.PhotoSlideItem
import com.tikigsaver.app.model.SavedMediaRecord
import com.tikigsaver.app.model.VideoQualityOption
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class DownloadRepository(
    private val context: Context,
    private val cookieStorage: CookieStorage
) {
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val desktopUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    suspend fun downloadVideo(
        media: MediaContent,
        option: VideoQualityOption,
        onProgress: (Float, String) -> Unit
    ): Result<SavedMediaRecord> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "dl_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val baseName = sanitizeFileName(media.title).ifBlank { "video_${System.currentTimeMillis()}" }
            val tag = when {
                option.upscaleTargetShort >= 1440 -> "2K"
                option.upscaleTargetShort >= 1080 -> "HD_1080p"
                option.isHd -> "HD"
                else -> "Normal"
            }
            val outTemplate = File(tempDir, "${baseName}_$tag.%(ext)s").absolutePath

            onProgress(0f, "Menyiapkan ${option.label}...")
            val ytdlpResult = runCatching {
                val req = YoutubeDLRequest(media.sourceUrl).apply {
                    addOption("-f", option.formatSpec)
                    if (option.upscaleTargetShort > 0) {
                        val t = option.upscaleTargetShort
                        val vf = "scale=if(lte(iw\\,ih)\\,$t\\,-2):if(lte(iw\\,ih)\\,-2\\,$t):flags=lanczos,unsharp=5:5:0.6:5:5:0.0"
                        addOption("--recode-video", "mp4")
                        addOption("--postprocessor-args", "VideoConvertor:-vf $vf -q:v 2 -c:a copy -movflags +faststart")
                    } else {
                        addOption("--merge-output-format", "mp4")
                    }
                    addOption("--no-playlist")
                    addOption("--no-mtime")
                    addOption("-o", outTemplate)
                    cookieStorage.getCookieFilePath()?.let { path ->
                        addOption("--cookies", path)
                    }
                }
                YoutubeDL.getInstance().execute(req, "vid_${UUID.randomUUID()}") { progress, eta, _ ->
                    val pct = progress.coerceIn(0f, 100f)
                    val etaText = if (eta > 0) " (${eta}s)" else ""
                    val label = if (option.upscaleTargetShort > 0) "Memproses ${option.label}" else "Mengunduh ${option.label}"
                    onProgress(pct, "$label: ${pct.toInt()}%$etaText")
                }
            }

            var downloadedFile = tempDir.listFiles()
                ?.firstOrNull { it.isFile && it.length() > 0 && !it.name.endsWith(".part") }

            if (downloadedFile == null && !option.directFallbackUrl.isNullOrBlank()) {
                onProgress(15f, "Mengunduh via fallback stream...")
                val fallbackTarget = File(tempDir, "${baseName}_$tag.mp4")
                downloadDirectHttp(option.directFallbackUrl, fallbackTarget) { pct ->
                    onProgress(pct, "Mengunduh ${option.label}: ${pct.toInt()}%")
                }
                downloadedFile = fallbackTarget.takeIf { it.exists() && it.length() > 0 }
            }

            val finalFile = downloadedFile ?: return@withContext Result.failure(
                ytdlpResult.exceptionOrNull() ?: IllegalStateException("File video gagal diunduh.")
            )

            val verifiedSpecs = VideoSpecsVerifier.inspectVideoFile(finalFile)
            val savedName = "${baseName}_${tag}_${System.currentTimeMillis() % 10000}.${finalFile.extension.ifBlank { "mp4" }}"
            val dlKind = when {
                option.upscaleTargetShort >= 1440 -> DownloadKind.VIDEO_2K
                option.isHd -> DownloadKind.VIDEO_HD
                else -> DownloadKind.VIDEO_NORMAL
            }
            val vaultPath = copyToVault(finalFile, savedName)
            val uri = saveToMediaStore(
                sourceFile = finalFile,
                displayName = savedName,
                mimeType = "video/mp4",
                kind = dlKind
            )

            Result.success(
                SavedMediaRecord(
                    id = UUID.randomUUID().toString(),
                    title = media.title,
                    kind = dlKind,
                    platform = media.platform,
                    fileUri = uri,
                    fileName = savedName,
                    fileSizeBytes = finalFile.length(),
                    verifiedSpecs = verifiedSpecs,
                    thumbnailUrl = media.thumbnailUrl,
                    sourceUrl = media.sourceUrl,
                    vaultPath = vaultPath
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    suspend fun downloadMp3(
        media: MediaContent,
        onProgress: (Float, String) -> Unit
    ): Result<SavedMediaRecord> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "mp3_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val baseName = sanitizeFileName(media.title).ifBlank { "audio_${System.currentTimeMillis()}" }
            onProgress(0f, "Mengekstrak audio MP3...")

            var downloadedFile: File? = null
            val outTemplate = File(tempDir, "$baseName.%(ext)s").absolutePath

            val ytdlpAttempt = runCatching {
                val req = YoutubeDLRequest(media.sourceUrl).apply {
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                    addOption("--no-playlist")
                    addOption("--no-mtime")
                    addOption("-o", outTemplate)
                    cookieStorage.getCookieFilePath()?.let { path ->
                        addOption("--cookies", path)
                    }
                }
                YoutubeDL.getInstance().execute(req, "mp3_${UUID.randomUUID()}") { progress, _, _ ->
                    val pct = progress.coerceIn(0f, 100f)
                    onProgress(pct, "Mengonversi ke MP3: ${pct.toInt()}%")
                }
            }

            downloadedFile = tempDir.listFiles()
                ?.firstOrNull { it.isFile && it.length() > 0 && !it.name.endsWith(".part") }

            if (downloadedFile == null && !media.mp3DirectUrl.isNullOrBlank()) {
                val directFile = File(tempDir, "$baseName.mp3")
                downloadDirectHttp(media.mp3DirectUrl, directFile) { pct ->
                    onProgress(pct, "Mengunduh MP3: ${pct.toInt()}%")
                }
                downloadedFile = directFile.takeIf { it.exists() && it.length() > 0 }
            }

            val finalFile = downloadedFile ?: return@withContext Result.failure(
                ytdlpAttempt.exceptionOrNull() ?: IllegalStateException("Audio MP3 tidak ditemukan.")
            )

            val ext = finalFile.extension.ifBlank { "mp3" }
            val savedName = "${baseName}_${System.currentTimeMillis() % 10000}.$ext"
            val vaultPath = copyToVault(finalFile, savedName)
            val uri = saveToMediaStore(
                sourceFile = finalFile,
                displayName = savedName,
                mimeType = if (ext.equals("mp3", true)) "audio/mpeg" else "audio/mp4",
                kind = DownloadKind.MP3
            )

            Result.success(
                SavedMediaRecord(
                    id = UUID.randomUUID().toString(),
                    title = media.title,
                    kind = DownloadKind.MP3,
                    platform = media.platform,
                    fileUri = uri,
                    fileName = savedName,
                    fileSizeBytes = finalFile.length(),
                    verifiedSpecs = "MP3 Audio",
                    thumbnailUrl = media.thumbnailUrl,
                    sourceUrl = media.sourceUrl,
                    vaultPath = vaultPath
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    suspend fun downloadPhotoSlide(
        media: MediaContent,
        photo: PhotoSlideItem,
        onProgress: (Float) -> Unit
    ): Result<SavedMediaRecord> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "img_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val baseName = sanitizeFileName(media.title).ifBlank { "photo" }
            val targetUrl = if (photo.isVideo && !photo.videoUrl.isNullOrBlank()) photo.videoUrl else photo.url
            val isVideo = photo.isVideo && !photo.videoUrl.isNullOrBlank()
            val ext = when {
                isVideo -> "mp4"
                targetUrl.contains(".png", ignoreCase = true) -> "png"
                targetUrl.contains(".webp", ignoreCase = true) -> "webp"
                else -> "jpg"
            }

            val tempFile = File(tempDir, "${baseName}_${photo.index}.$ext")
            downloadDirectHttp(targetUrl, tempFile, onProgress)

            val savedName = "${baseName}_slide${photo.index}_${System.currentTimeMillis() % 10000}.$ext"
            val mime = when {
                isVideo -> "video/mp4"
                ext == "png" -> "image/png"
                ext == "webp" -> "image/webp"
                else -> "image/jpeg"
            }

            val vaultPath = copyToVault(tempFile, savedName)
            val uri = saveToMediaStore(
                sourceFile = tempFile,
                displayName = savedName,
                mimeType = mime,
                kind = if (isVideo) DownloadKind.VIDEO_HD else DownloadKind.IMAGE
            )

            val verified = if (isVideo) VideoSpecsVerifier.inspectVideoFile(tempFile) else {
                if (photo.width > 0 && photo.height > 0) "${photo.width}×${photo.height}" else "Resolusi Asli"
            }

            Result.success(
                SavedMediaRecord(
                    id = UUID.randomUUID().toString(),
                    title = "${media.title} (Slide ${photo.index})",
                    kind = if (isVideo) DownloadKind.VIDEO_HD else DownloadKind.IMAGE,
                    platform = media.platform,
                    fileUri = uri,
                    fileName = savedName,
                    fileSizeBytes = tempFile.length(),
                    verifiedSpecs = verified,
                    thumbnailUrl = photo.url,
                    sourceUrl = media.sourceUrl,
                    vaultPath = vaultPath
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun downloadDirectHttp(
        url: String,
        dest: File,
        onProgress: (Float) -> Unit
    ) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", desktopUserAgent)
            .header("Referer", "https://www.tiktok.com/")
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code} saat mengunduh media.")
            }
            val body = resp.body ?: throw IllegalStateException("Response body kosong.")
            val total = body.contentLength()
            body.byteStream().use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            onProgress(((downloaded * 100f) / total).coerceIn(0f, 100f))
                        }
                    }
                    output.flush()
                }
            }
            onProgress(100f)
        }
    }

    private fun copyToVault(sourceFile: File, savedName: String): String? = runCatching {
        val vaultDir = File(context.filesDir, "vault").apply { if (!exists()) mkdirs() }
        val target = File(vaultDir, savedName)
        sourceFile.copyTo(target, overwrite = true)
        target.absolutePath
    }.getOrNull()

    private fun saveToMediaStore(
        sourceFile: File,
        displayName: String,
        mimeType: String,
        kind: DownloadKind
    ): Uri {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val (collection, relativePath) = when (kind) {
                DownloadKind.VIDEO_2K, DownloadKind.VIDEO_HD, DownloadKind.VIDEO_NORMAL ->
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MOVIES}/ElyReneDownloader"
                DownloadKind.MP3 ->
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MUSIC}/ElyReneDownloader"
                DownloadKind.IMAGE ->
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_PICTURES}/ElyReneDownloader"
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Gagal membuat file di MediaStore.")

            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out) }
            } ?: throw IllegalStateException("Gagal menulis file ke MediaStore.")

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } else {
            val dirType = when (kind) {
                DownloadKind.VIDEO_2K, DownloadKind.VIDEO_HD, DownloadKind.VIDEO_NORMAL -> Environment.DIRECTORY_MOVIES
                DownloadKind.MP3 -> Environment.DIRECTORY_MUSIC
                DownloadKind.IMAGE -> Environment.DIRECTORY_PICTURES
            }
            val targetDir = File(Environment.getExternalStoragePublicDirectory(dirType), "TikIG Saver")
            if (!targetDir.exists()) targetDir.mkdirs()
            val targetFile = File(targetDir, displayName)
            sourceFile.copyTo(targetFile, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf(mimeType), null)
            return Uri.fromFile(targetFile)
        }
    }

    private fun sanitizeFileName(raw: String): String =
        raw.replace(Regex("""[\\/:*?"<>|\n\r]+"""), " ")
            .trim()
            .take(50)
            .replace(Regex("""\s+"""), "_")
}
