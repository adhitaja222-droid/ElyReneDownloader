package com.tikigsaver.app.engine

import android.content.Context
import android.util.Log
import com.tikigsaver.app.model.MediaContent
import com.tikigsaver.app.model.PhotoSlideItem
import com.tikigsaver.app.model.Platform
import com.tikigsaver.app.model.VideoQualityOption
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class MediaExtractorRepository(
    context: Context,
    private val cookieStorage: CookieStorage
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val desktopUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    suspend fun extractMedia(rawUrl: String): Result<MediaContent> = withContext(Dispatchers.IO) {
        val url = UrlUtils.extractSupportedUrl(rawUrl)
            ?: return@withContext Result.failure(IllegalArgumentException("Masukkan link TikTok atau Instagram yang valid."))

        val platform = UrlUtils.detectPlatform(url)
        val ytdlpResult = runCatching { fetchViaYtDlp(url, platform) }

        return@withContext when (platform) {
            Platform.TIKTOK -> extractTikTok(url, ytdlpResult.getOrNull(), ytdlpResult.exceptionOrNull())
            Platform.INSTAGRAM -> extractInstagram(url, ytdlpResult.getOrNull(), ytdlpResult.exceptionOrNull())
            Platform.UNKNOWN -> Result.failure(IllegalArgumentException("Platform tidak didukung."))
        }
    }

    private fun fetchViaYtDlp(url: String, platform: Platform): JsonObject {
        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-single-json")
            addOption("--no-warnings")
            addOption("--ignore-errors")
            if (platform == Platform.INSTAGRAM) {
                cookieStorage.getCookieFilePath()?.let { path ->
                    addOption("--cookies", path)
                }
            }
        }
        val response = YoutubeDL.getInstance().execute(request)
        val raw = response.out.trim()
        val jsonLine = raw.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("{") && it.endsWith("}") }
            ?: raw
        return json.parseToJsonElement(jsonLine).jsonObject
    }

    private fun extractTikTok(
        url: String,
        ytJson: JsonObject?,
        ytError: Throwable?
    ): Result<MediaContent> {
        // Check TikTok Photo Mode / Slideshow & HD fallback via TikTok Web JSON and TikWM
        val tikTokExtra = runCatching { fetchTikTokPhotoOrFallback(url) }.getOrNull()

        if (ytJson != null) {
            val formats = parseFormats(ytJson["formats"]?.jsonArray)
            val title = ytJson.str("description").ifBlank { ytJson.str("title") }.ifBlank {
                tikTokExtra?.title ?: "Video TikTok"
            }
            val author = ytJson.str("uploader").ifBlank { ytJson.str("creator") }.ifBlank {
                tikTokExtra?.author.orEmpty()
            }
            val thumb = ytJson.str("thumbnail").ifBlank { tikTokExtra?.thumbnailUrl.orEmpty() }
            val duration = ytJson.int("duration")

            val photos = tikTokExtra?.photos.orEmpty()
            val selected = if (formats.isNotEmpty() && photos.isEmpty()) {
                FormatSelector.selectVideoOptions(formats)
            } else {
                null
            }

            // If it's a TikTok Photo Mode post, yt-dlp only grabs the audio; combine photos + audio + optional fallback video
            if (photos.isNotEmpty()) {
                return Result.success(
                    MediaContent(
                        sourceUrl = url,
                        platform = Platform.TIKTOK,
                        title = title,
                        author = author,
                        thumbnailUrl = photos.firstOrNull()?.url ?: thumb.ifBlank { null },
                        durationSeconds = duration,
                        hdVideo = tikTokExtra?.hdVideo,
                        normalVideo = tikTokExtra?.normalVideo,
                        hasMp3 = true,
                        mp3DirectUrl = tikTokExtra?.mp3DirectUrl,
                        photos = photos,
                        extractorSource = "yt-dlp + TikTok Photo Extractor"
                    )
                )
            }

            if (selected != null) {
                return Result.success(
                    MediaContent(
                        sourceUrl = url,
                        platform = Platform.TIKTOK,
                        title = title,
                        author = author,
                        thumbnailUrl = thumb.ifBlank { null },
                        durationSeconds = duration,
                        hdVideo = selected.hd.copy(
                            directFallbackUrl = selected.hd.directFallbackUrl ?: tikTokExtra?.hdVideo?.directFallbackUrl
                        ),
                        normalVideo = selected.normal.copy(
                            directFallbackUrl = selected.normal.directFallbackUrl ?: tikTokExtra?.normalVideo?.directFallbackUrl
                        ),
                        ultraVideo = selected.ultra?.copy(
                            directFallbackUrl = selected.ultra.directFallbackUrl ?: tikTokExtra?.hdVideo?.directFallbackUrl
                        ),
                        hasMp3 = true,
                        mp3DirectUrl = tikTokExtra?.mp3DirectUrl,
                        photos = emptyList(),
                        extractorSource = "yt-dlp (On-Device)"
                    )
                )
            }
        }

        if (tikTokExtra != null) {
            return Result.success(tikTokExtra)
        }

        return Result.failure(
            ytError ?: IllegalStateException("Gagal mengambil media TikTok. Pastikan link publik & coba lagi.")
        )
    }

    private fun extractInstagram(
        url: String,
        ytJson: JsonObject?,
        ytError: Throwable?
    ): Result<MediaContent> {
        if (ytJson != null) {
            val entries = ytJson["entries"]?.jsonArray
            if (entries != null && entries.isNotEmpty()) {
                return Result.success(parseInstagramEntries(url, ytJson, entries))
            }

            val formats = parseFormats(ytJson["formats"]?.jsonArray)
            val title = ytJson.str("description").ifBlank { ytJson.str("title") }.ifBlank { "Postingan Instagram" }
            val author = ytJson.str("uploader").ifBlank { ytJson.str("channel") }
            val thumb = ytJson.str("thumbnail").ifBlank { null }
            val duration = ytJson.int("duration")

            if (formats.isNotEmpty()) {
                val selected = FormatSelector.selectVideoOptions(formats)
                return Result.success(
                    MediaContent(
                        sourceUrl = url,
                        platform = Platform.INSTAGRAM,
                        title = title,
                        author = author,
                        thumbnailUrl = thumb,
                        durationSeconds = duration,
                        hdVideo = selected.hd,
                        normalVideo = selected.normal,
                        ultraVideo = selected.ultra,
                        hasMp3 = true,
                        extractorSource = "yt-dlp (On-Device)"
                    )
                )
            }

            // Single image post returned by yt-dlp without video formats
            val igEmbed = runCatching { fetchInstagramEmbedFallback(url) }.getOrNull()
            if (igEmbed != null && igEmbed.photos.isNotEmpty()) {
                return Result.success(igEmbed)
            }
            if (!thumb.isNullOrBlank()) {
                return Result.success(
                    MediaContent(
                        sourceUrl = url,
                        platform = Platform.INSTAGRAM,
                        title = title,
                        author = author,
                        thumbnailUrl = thumb,
                        hasMp3 = false,
                        photos = listOf(PhotoSlideItem(index = 1, url = thumb)),
                        extractorSource = "yt-dlp"
                    )
                )
            }
        }

        val embedFallback = runCatching { fetchInstagramEmbedFallback(url) }.getOrNull()
        if (embedFallback != null) {
            return Result.success(embedFallback)
        }

        val errMsg = ytError?.message.orEmpty()
        val friendly = if (errMsg.contains("login", ignoreCase = true) ||
            errMsg.contains("cookie", ignoreCase = true) ||
            errMsg.contains("401", ignoreCase = true) ||
            errMsg.contains("rate-limit", ignoreCase = true)
        ) {
            "Instagram meminta login untuk postingan ini. Silakan login akun IG di tab Pengaturan (Cookie) lalu coba lagi."
        } else {
            "Gagal mengambil media Instagram. Coba login IG di tab Pengaturan atau periksa link."
        }
        return Result.failure(IllegalStateException(friendly))
    }

    private fun parseInstagramEntries(
        url: String,
        root: JsonObject,
        entries: JsonArray
    ): MediaContent {
        val photos = mutableListOf<PhotoSlideItem>()
        var firstVideoHd: VideoQualityOption? = null
        var firstVideoNormal: VideoQualityOption? = null

        entries.forEachIndexed { idx, elem ->
            val obj = elem.jsonObject
            val formats = parseFormats(obj["formats"]?.jsonArray)
            val thumb = obj.str("thumbnail")
            val directImgUrl = obj.str("url").takeIf { it.isNotBlank() && !it.contains(".mp4") }
            if (formats.isNotEmpty()) {
                val selected = FormatSelector.selectVideoOptions(formats)
                if (firstVideoHd == null) {
                    firstVideoHd = selected.hd
                    firstVideoNormal = selected.normal
                }
                photos.add(
                    PhotoSlideItem(
                        index = idx + 1,
                        url = thumb.ifBlank { selected.hd.directFallbackUrl.orEmpty() },
                        width = selected.hd.width,
                        height = selected.hd.height,
                        isVideo = true,
                        videoUrl = selected.hd.directFallbackUrl
                    )
                )
            } else {
                val imgUrl = directImgUrl ?: thumb
                if (imgUrl.isNotBlank()) {
                    photos.add(PhotoSlideItem(index = idx + 1, url = imgUrl))
                }
            }
        }

        val title = root.str("description").ifBlank { root.str("title") }.ifBlank { "Carousel Instagram" }
        val author = root.str("uploader").ifBlank { root.str("channel") }
        val thumb = root.str("thumbnail").ifBlank { photos.firstOrNull()?.url.orEmpty() }

        return MediaContent(
            sourceUrl = url,
            platform = Platform.INSTAGRAM,
            title = title,
            author = author,
            thumbnailUrl = thumb.ifBlank { null },
            hdVideo = firstVideoHd,
            normalVideo = firstVideoNormal,
            ultraVideo = null,
            hasMp3 = firstVideoHd != null,
            photos = photos,
            extractorSource = "yt-dlp Carousel (On-Device)"
        )
    }

    private fun fetchTikTokPhotoOrFallback(url: String): MediaContent? {
        // 1. Try TikTok Web HTML __UNIVERSAL_DATA_FOR_REHYDRATION__ for original imagePost & video streams
        runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", desktopUserAgent)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string().orEmpty()
                    val parsed = parseTikTokUniversalHtml(url, html)
                    if (parsed != null) return parsed
                }
            }
        }

        // 2. Fallback to TikWM JSON API (especially reliable for TikTok /photo/ slideshows)
        return runCatching {
            val encoded = URLEncoder.encode(url, "UTF-8")
            val req = Request.Builder()
                .url("https://www.tikwm.com/api/?url=$encoded&hd=1")
                .header("User-Agent", desktopUserAgent)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                if (root.int("code") != 0) return@use null
                val data = root["data"]?.jsonObject ?: return@use null

                val title = data.str("title").ifBlank { "Postingan TikTok" }
                val author = data["author"]?.jsonObject?.str("unique_id").orEmpty()
                val cover = data.str("cover").ifBlank { data.str("origin_cover") }
                val music = data.str("music").ifBlank { data["music_info"]?.jsonObject?.str("play").orEmpty() }

                val imagesArray = data["images"]?.jsonArray
                val photos = imagesArray?.mapIndexedNotNull { idx, item ->
                    val imgUrl = item.jsonPrimitive.contentOrNull
                    if (imgUrl.isNullOrBlank()) null else PhotoSlideItem(index = idx + 1, url = imgUrl)
                }.orEmpty()

                val hdUrl = data.str("hdplay")
                val normalUrl = data.str("play")
                val hdSize = data.long("hd_size")
                val normalSize = data.long("size")
                val hdOption = if (hdUrl.isNotBlank() && photos.isEmpty()) {
                    VideoQualityOption(
                        label = "Download Video HD",
                        formatSpec = "bestvideo+bestaudio/best",
                        fileSizeBytes = hdSize,
                        directFallbackUrl = hdUrl,
                        isHd = true
                    )
                } else null

                val normalOption = if (normalUrl.isNotBlank() && photos.isEmpty()) {
                    VideoQualityOption(
                        label = "Download Video",
                        formatSpec = "bestvideo[height<=576]+bestaudio/best[height<=576]/best[height<=720]/best[height<=720]/best",
                        fileSizeBytes = if (hdSize > 0L && normalSize >= hdSize) 0L else normalSize,
                        directFallbackUrl = normalUrl,
                        isHd = false
                    )
                } else null

                MediaContent(
                    sourceUrl = url,
                    platform = Platform.TIKTOK,
                    title = title,
                    author = author,
                    thumbnailUrl = photos.firstOrNull()?.url ?: cover.ifBlank { null },
                    durationSeconds = data.int("duration"),
                    hdVideo = hdOption,
                    normalVideo = normalOption,
                    hasMp3 = music.isNotBlank() || photos.isEmpty(),
                    mp3DirectUrl = music.ifBlank { null },
                    photos = photos,
                    extractorSource = "TikTok Slideshow Extractor"
                )
            }
        }.getOrNull()
    }

    private fun parseTikTokUniversalHtml(url: String, html: String): MediaContent? {
        val marker = """<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">"""
        val start = html.indexOf(marker)
        if (start < 0) return null
        val jsonStart = start + marker.length
        val end = html.indexOf("</script>", jsonStart)
        if (end < 0) return null

        val root = json.parseToJsonElement(html.substring(jsonStart, end)).jsonObject
        val defaultScope = root["__DEFAULT_SCOPE__"]?.jsonObject ?: return null
        val itemStruct = defaultScope["webapp.video-detail"]
            ?.jsonObject?.get("itemInfo")
            ?.jsonObject?.get("itemStruct")
            ?.jsonObject ?: return null

        val title = itemStruct.str("desc").ifBlank { "Postingan TikTok" }
        val author = itemStruct["author"]?.jsonObject?.str("uniqueId").orEmpty()
        val musicUrl = itemStruct["music"]?.jsonObject?.str("playUrl").orEmpty()

        val imageList = itemStruct["imagePost"]?.jsonObject?.get("images")?.jsonArray
        val photos = imageList?.mapIndexedNotNull { idx, elem ->
            val imgObj = elem.jsonObject
            val urlList = imgObj["imageURL"]?.jsonObject?.get("urlList")?.jsonArray
            val bestUrl = urlList?.firstOrNull()?.jsonPrimitive?.contentOrNull
            if (bestUrl.isNullOrBlank()) null else {
                PhotoSlideItem(
                    index = idx + 1,
                    url = bestUrl,
                    width = imgObj.int("imageWidth"),
                    height = imgObj.int("imageHeight")
                )
            }
        }.orEmpty()

        if (photos.isEmpty()) return null

        val cover = itemStruct["imagePost"]?.jsonObject
            ?.get("cover")?.jsonObject
            ?.get("imageURL")?.jsonObject
            ?.get("urlList")?.jsonArray
            ?.firstOrNull()?.jsonPrimitive?.contentOrNull
            ?: photos.first().url

        return MediaContent(
            sourceUrl = url,
            platform = Platform.TIKTOK,
            title = title,
            author = author,
            thumbnailUrl = cover,
            hdVideo = null,
            normalVideo = null,
            hasMp3 = musicUrl.isNotBlank(),
            mp3DirectUrl = musicUrl.ifBlank { null },
            photos = photos,
            extractorSource = "TikTok Direct Photo Extractor"
        )
    }

    private fun fetchInstagramEmbedFallback(url: String): MediaContent? {
        val shortcode = UrlUtils.extractInstagramShortcode(url) ?: return null
        val cleaned = mutableListOf<String>()

        // 1. Try embed/captioned/ (supports carousels when available)
        runCatching {
            val embedReq = Request.Builder()
                .url("https://www.instagram.com/p/$shortcode/embed/captioned/")
                .header("User-Agent", desktopUserAgent)
                .build()
            httpClient.newCall(embedReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string().orEmpty()
                        .replace("\\/", "/")
                        .replace("\\u0026", "&")
                        .replace("&amp;", "&")
                    Regex("""https://[^"'\s<>]+\.(?:jpg|jpeg|webp)[^"'\s<>]*""")
                        .findAll(html)
                        .map { it.value }
                        .filter { !it.contains("s150x150") && !it.contains("profile_pic") && !it.contains("static.cdninstagram.com") }
                        .distinct()
                        .forEach { cleaned.add(it) }
                }
            }
        }

        // 2. Fallback to /media/?size=l (works for public IG photo posts without cookies)
        if (cleaned.isEmpty()) {
            runCatching {
                val mediaReq = Request.Builder()
                    .url("https://www.instagram.com/p/$shortcode/media/?size=l")
                    .header("User-Agent", desktopUserAgent)
                    .build()
                httpClient.newCall(mediaReq).execute().use { resp ->
                    val finalUrl = resp.request.url.toString()
                    val ctype = resp.header("Content-Type").orEmpty().lowercase()
                    if (resp.isSuccessful && ctype.contains("image/") && finalUrl.isNotBlank()) {
                        cleaned.add(finalUrl)
                    }
                }
            }
        }

        if (cleaned.isEmpty()) return null

        var title = "Postingan Instagram ($shortcode)"
        var author = ""
        runCatching {
            val oembedReq = Request.Builder()
                .url("https://www.instagram.com/api/v1/oembed/?url=${URLEncoder.encode("https://www.instagram.com/p/$shortcode/", "UTF-8")}")
                .header("User-Agent", desktopUserAgent)
                .build()
            httpClient.newCall(oembedReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    obj.str("title").takeIf { it.isNotBlank() }?.let { title = it }
                    obj.str("author_name").takeIf { it.isNotBlank() }?.let { author = it }
                }
            }
        }

        val photos = cleaned.mapIndexed { idx, img -> PhotoSlideItem(index = idx + 1, url = img) }
        return MediaContent(
            sourceUrl = url,
            platform = Platform.INSTAGRAM,
            title = title,
            author = author,
            thumbnailUrl = photos.first().url,
            hasMp3 = false,
            photos = photos,
            extractorSource = "Instagram Photo Extractor"
        )
    }

    private fun parseFormats(arr: JsonArray?): List<ParsedFormat> {
        if (arr == null) return emptyList()
        return arr.mapNotNull { elem ->
            val obj = elem as? JsonObject ?: return@mapNotNull null
            ParsedFormat(
                formatId = obj.str("format_id"),
                ext = obj.str("ext"),
                width = obj.int("width"),
                height = obj.int("height"),
                fps = obj.double("fps"),
                tbr = obj.double("tbr"),
                abr = obj.double("abr"),
                vcodec = obj.str("vcodec"),
                acodec = obj.str("acodec"),
                fileSize = obj.long("filesize").takeIf { it > 0L } ?: obj.long("filesize_approx"),
                formatNote = obj.str("format_note"),
                url = obj.str("url")
            )
        }
    }

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonElement)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonElement)?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() } ?: 0

    private fun JsonObject.long(key: String): Long =
        (this[key] as? JsonElement)?.let { runCatching { it.jsonPrimitive.longOrNull }.getOrNull() } ?: 0L

    private fun JsonObject.double(key: String): Double =
        (this[key] as? JsonElement)?.let { runCatching { it.jsonPrimitive.doubleOrNull }.getOrNull() } ?: 0.0
}
