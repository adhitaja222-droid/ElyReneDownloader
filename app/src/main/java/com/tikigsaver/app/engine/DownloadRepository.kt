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
import com.tikigsaver.app.model.Platform
import com.tikigsaver.app.model.SavedMediaRecord
import com.tikigsaver.app.model.VideoQualityOption
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
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
                    addOption("--user-agent", desktopUserAgent)
                    if (media.platform == Platform.TIKTOK) {
                        addOption("--referer", "https://www.tiktok.com/")
                    }
                    when {
                        option.upscaleTargetShort > 0 -> {
                            val t = option.upscaleTargetShort
                            val vf = "scale=if(lte(iw\\,ih)\\,$t\\,-2):if(lte(iw\\,ih)\\,-2\\,$t):flags=lanczos,unsharp=5:5:0.6:5:5:0.0"
                            addOption("--recode-video", "mp4")
                            addOption("--postprocessor-args", "VideoConvertor:-vf $vf -q:v 2 -c:a copy -movflags +faststart")
                        }
                        option.compressToShort > 0 -> {
                            val c = option.compressToShort
                            val vf = "scale=if(lte(iw\\,ih)\\,$c\\,-2):if(lte(iw\\,ih)\\,-2\\,$c)"
                            addOption("--recode-video", "mp4")
                            addOption("--postprocessor-args", "VideoConvertor:-vf $vf -q:v 5 -c:a aac -b:a 96k -movflags +faststart")
                        }
                        else -> {
                            addOption("--merge-output-format", "mp4")
                        }
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
                    val label = if (option.upscaleTargetShort > 0 || option.compressToShort > 0) "Memproses ${option.label}" else "Mengunduh ${option.label}"
                    onProgress(pct, "$label: ${pct.toInt()}%$etaText")
                }
            }

            var downloadedFile = tempDir.listFiles()
                ?.firstOrNull { it.isFile && it.length() > 0 && !it.name.endsWith(".part") }

            var fallbackErr: Throwable? = null
            if (downloadedFile == null && !option.directFallbackUrl.isNullOrBlank()) {
                onProgress(15f, "Mengunduh via fallback stream...")
                val fallbackTarget = File(tempDir, "${baseName}_$tag.mp4")
                val fbRes = runCatching {
                    downloadDirectHttp(option.directFallbackUrl, fallbackTarget) { pct ->
                        onProgress(pct, "Mengunduh ${option.label}: ${pct.toInt()}%")
                    }
                }
                fallbackErr = fbRes.exceptionOrNull()
                downloadedFile = fallbackTarget.takeIf { fbRes.isSuccess && it.exists() && it.length() > 0 }
            }

            // Fresh TikWM fallback if yt-dlp and directFallbackUrl both failed (e.g. HTTP 403)
            if (downloadedFile == null && media.platform == Platform.TIKTOK) {
                onProgress(20f, "Mengambil ulang stream alternatif...")
                val tikwmUrl = fetchTikWmFreshUrl(media.sourceUrl, isHd = option.isHd, isAudio = false)
                if (!tikwmUrl.isNullOrBlank()) {
                    val tikwmTarget = File(tempDir, "${baseName}_$tag.mp4")
                    val twRes = runCatching {
                        downloadDirectHttp(tikwmUrl, tikwmTarget) { pct ->
                            onProgress(pct, "Mengunduh ${option.label}: ${pct.toInt()}%")
                        }
                    }
                    fallbackErr = twRes.exceptionOrNull()
                    downloadedFile = tikwmTarget.takeIf { twRes.isSuccess && it.exists() && it.length() > 0 }
                }
            }

            val finalFile = downloadedFile ?: return@withContext Result.failure(
                fallbackErr ?: ytdlpResult.exceptionOrNull() ?: IllegalStateException("File video gagal diunduh.")
            )

            val verifiedSpecs = VideoSpecsVerifier.inspectVideoFile(finalFile)
            val savedName = "${baseName}_${tag}_${System.currentTimeMillis() % 10000}.${finalFile.extension.ifBlank { "mp4" }}"
            val dlKind = when {
                option.upscaleTargetShort >= 1440 -> DownloadKind.VIDEO_2K
                option.isHd -> DownloadKind.VIDEO_HD
                else -> DownloadKind.VIDEO_NORMAL
            }
            val vaultPath = copyToVault(finalFile, savedName)
            val thumbDir = File(context.filesDir, "thumbs")
            val localThumbPath = VideoEditorRepository.extractThumbFile(
                finalFile,
                File(thumbDir, "${finalFile.nameWithoutExtension}_thumb.jpg")
            )
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
                    vaultPath = vaultPath,
                    localThumbPath = localThumbPath
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
                    addOption("--user-agent", desktopUserAgent)
                    if (media.platform == Platform.TIKTOK) {
                        addOption("--referer", "https://www.tiktok.com/")
                    }
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
                val mp3Res = runCatching {
                    downloadDirectHttp(media.mp3DirectUrl, directFile) { pct ->
                        onProgress(pct, "Mengunduh MP3: ${pct.toInt()}%")
                    }
                }
                downloadedFile = directFile.takeIf { mp3Res.isSuccess && it.exists() && it.length() > 0 }
            }

            if (downloadedFile == null && media.platform == Platform.TIKTOK) {
                val tikwmMp3 = fetchTikWmFreshUrl(media.sourceUrl, isHd = false, isAudio = true)
                if (!tikwmMp3.isNullOrBlank()) {
                    val directFile = File(tempDir, "$baseName.mp3")
                    val twRes = runCatching {
                        downloadDirectHttp(tikwmMp3, directFile) { pct ->
                            onProgress(pct, "Mengunduh MP3: ${pct.toInt()}%")
                        }
                    }
                    downloadedFile = directFile.takeIf { twRes.isSuccess && it.exists() && it.length() > 0 }
                }
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
                    vaultPath = vaultPath,
                    localThumbPath = if (!isVideo) vaultPath else null
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

    private fun fetchTikWmFreshUrl(sourceUrl: String, isHd: Boolean, isAudio: Boolean): String? = runCatching {
        val encoded = URLEncoder.encode(sourceUrl, "UTF-8")
        val req = Request.Builder()
            .url("https://www.tikwm.com/api/?url=$encoded&hd=1")
            .header("User-Agent", desktopUserAgent)
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val root = JSONObject(resp.body?.string().orEmpty())
            if (root.optInt("code", -1) != 0) return@use null
            val data = root.optJSONObject("data") ?: return@use null
            when {
                isAudio -> data.optString("music").ifBlank {
                    data.optJSONObject("music_info")?.optString("play").orEmpty()
                }.ifBlank { null }
                isHd -> data.optString("hdplay").ifBlank { data.optString("play") }.ifBlank { null }
                else -> data.optString("play").ifBlank { data.optString("hdplay") }.ifBlank { null }
            }
        }
    }.getOrNull()

    private fun copyToVault(sourceFile: File, savedName: String): String? = runCatching {
        val vaultDir = File(context.filesDir, "vault").apply { if (!exists()) mkdirs() }
        val target = File(vaultDir, savedName)
        sourceFile.copyTo(target, overwrite = true)
        target.absolutePath
    }.getOrNull()

    internal fun saveToMediaStore(
        sourceFile: File,
        displayName: String,
        mimeType: String,
        kind: DownloadKind
    ): Uri {
        val resolver = context.contentResolver
        val nowMs = System.currentTimeMillis()
        val nowSec = nowMs / 1000L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val (collection, relativePath) = when (kind) {
                DownloadKind.VIDEO_2K, DownloadKind.VIDEO_HD, DownloadKind.VIDEO_NORMAL ->
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_DCIM}/Camera"
                DownloadKind.MP3 ->
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MUSIC}/ElyReneDownloader"
                DownloadKind.IMAGE ->
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_DCIM}/Camera"
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.DATE_ADDED, nowSec)
                put(MediaStore.MediaColumns.DATE_MODIFIED, nowSec)
                if (kind != DownloadKind.MP3) {
                    put(MediaStore.MediaColumns.DATE_TAKEN, nowMs)
                }
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Gagal membuat file di MediaStore.")

            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out) }
            } ?: throw IllegalStateException("Gagal menulis file ke MediaStore.")

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            values.put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
            if (kind != DownloadKind.MP3) {
                values.put(MediaStore.MediaColumns.DATE_TAKEN, System.currentTimeMillis())
            }
            resolver.update(uri, values, null, null)
            resolver.notifyChange(uri, null)

            // Trigger MediaScanner on the resolved path so WhatsApp "Terbaru" indexes dimensions/duration immediately
            runCatching {
                resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val path = c.getString(0)
                        if (!path.isNullOrBlank()) {
                            MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mimeType), null)
                        }
                    }
                }
            }
            return uri
        } else {
            val (dirType, subDir) = when (kind) {
                DownloadKind.VIDEO_2K, DownloadKind.VIDEO_HD, DownloadKind.VIDEO_NORMAL -> Environment.DIRECTORY_DCIM to "Camera"
                DownloadKind.MP3 -> Environment.DIRECTORY_MUSIC to "ElyReneDownloader"
                DownloadKind.IMAGE -> Environment.DIRECTORY_DCIM to "Camera"
            }
            val targetDir = File(Environment.getExternalStoragePublicDirectory(dirType), subDir)
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
