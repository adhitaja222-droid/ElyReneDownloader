package com.tikigsaver.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tikigsaver.app.TikIGApp
import com.tikigsaver.app.engine.CookieStorage
import com.tikigsaver.app.engine.DownloadRepository
import com.tikigsaver.app.engine.MediaExtractorRepository
import com.tikigsaver.app.engine.UrlUtils
import com.tikigsaver.app.engine.VideoEditorRepository
import com.tikigsaver.app.engine.VideoSpecsVerifier
import com.tikigsaver.app.model.DownloadKind
import com.tikigsaver.app.model.MediaContent
import com.tikigsaver.app.model.MyEditRecord
import com.tikigsaver.app.model.PhotoSlideItem
import com.tikigsaver.app.model.Platform
import com.tikigsaver.app.model.SavedMediaRecord
import com.tikigsaver.app.model.VideoQualityOption
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class MainUiState(
    val urlInput: String = "",
    val isAnalyzing: Boolean = false,
    val mediaContent: MediaContent? = null,
    val errorMessage: String? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadStatus: String = "",
    val downloadingPhotoIndex: Int? = null,
    val downloadedPhotoIndices: Set<Int> = emptySet(),
    val lastSuccessBanner: String? = null,
    val savedHistory: List<SavedMediaRecord> = emptyList(),
    val myEdits: List<MyEditRecord> = emptyList(),
    val isEditingVideo: Boolean = false,
    val hasInstagramCookies: Boolean = false,
    val isUpdatingYtDlp: Boolean = false,
    val engineUpdateMessage: String? = null,
    val isCheckingApkUpdate: Boolean = false,
    val availableUpdateVersion: String? = null,
    val availableUpdateNotes: String? = null,
    val availableUpdateApkUrl: String? = null,
    val isDownloadingApkUpdate: Boolean = false,
    val apkDownloadProgress: Float = 0f,
    val apkDownloadedBytes: Long = 0L,
    val apkTotalBytes: Long = 0L,
    val downloadedApkFile: File? = null,
    val isPreparingWaHd: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val cookieStorage = CookieStorage(application)
    private val extractor = MediaExtractorRepository(application, cookieStorage)
    private val downloader = DownloadRepository(application, cookieStorage)
    private val prefs = application.getSharedPreferences("elyrene_prefs", Context.MODE_PRIVATE)

    val engineState = (application as TikIGApp).engineState

    private val _uiState = MutableStateFlow(
        MainUiState(
            hasInstagramCookies = cookieStorage.hasInstagramCookies(),
            savedHistory = loadHistoryFromPrefs(),
            myEdits = loadMyEditsFromPrefs()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private fun saveHistoryToPrefs(list: List<SavedMediaRecord>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("title", r.title)
                put("kind", r.kind.name)
                put("platform", r.platform.name)
                put("fileUri", r.fileUri.toString())
                put("fileName", r.fileName)
                put("fileSizeBytes", r.fileSizeBytes)
                put("verifiedSpecs", r.verifiedSpecs ?: "")
                put("thumbnailUrl", r.thumbnailUrl ?: "")
                put("sourceUrl", r.sourceUrl)
                put("vaultPath", r.vaultPath ?: "")
                put("localThumbPath", r.localThumbPath ?: "")
                put("timestampMillis", r.timestampMillis)
            })
        }
        prefs.edit().putString("history_json", arr.toString()).apply()
    }

    private fun loadHistoryFromPrefs(): List<SavedMediaRecord> = runCatching {
        val raw = prefs.getString("history_json", null) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            SavedMediaRecord(
                id = o.optString("id"),
                title = o.optString("title"),
                kind = runCatching { DownloadKind.valueOf(o.optString("kind")) }.getOrDefault(DownloadKind.VIDEO_HD),
                platform = runCatching { Platform.valueOf(o.optString("platform")) }.getOrDefault(Platform.TIKTOK),
                fileUri = Uri.parse(o.optString("fileUri")),
                fileName = o.optString("fileName"),
                fileSizeBytes = o.optLong("fileSizeBytes"),
                verifiedSpecs = o.optString("verifiedSpecs").ifBlank { null },
                thumbnailUrl = o.optString("thumbnailUrl").ifBlank { null },
                sourceUrl = o.optString("sourceUrl"),
                vaultPath = o.optString("vaultPath").ifBlank { null },
                localThumbPath = o.optString("localThumbPath").ifBlank { null },
                timestampMillis = o.optLong("timestampMillis", System.currentTimeMillis())
            )
        }
    }.getOrDefault(emptyList())

    private fun appendHistory(records: List<SavedMediaRecord>) {
        _uiState.update { state ->
            val updated = records + state.savedHistory
            saveHistoryToPrefs(updated)
            state.copy(savedHistory = updated)
        }
    }

    fun removeHistoryItem(id: String) {
        _uiState.update { state ->
            val target = state.savedHistory.find { it.id == id }
            target?.vaultPath?.let { path -> runCatching { File(path).delete() } }
            val updated = state.savedHistory.filterNot { it.id == id }
            saveHistoryToPrefs(updated)
            state.copy(savedHistory = updated)
        }
    }

    fun clearAllHistory() {
        _uiState.update { state ->
            state.savedHistory.forEach { it.vaultPath?.let { p -> runCatching { File(p).delete() } } }
            runCatching { File(getApplication<Application>().filesDir, "vault").deleteRecursively() }
            saveHistoryToPrefs(emptyList())
            state.copy(savedHistory = emptyList())
        }
    }

    fun reDownloadFromHistory(record: SavedMediaRecord) {
        if (record.sourceUrl.isBlank()) return
        _uiState.update { it.copy(urlInput = record.sourceUrl, errorMessage = null) }
        analyzeCurrentUrl(record.sourceUrl)
    }

    private fun saveMyEditsToPrefs(list: List<MyEditRecord>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("title", e.title)
                put("filePath", e.filePath)
                put("fileName", e.fileName)
                put("fileSizeBytes", e.fileSizeBytes)
                put("verifiedSpecs", e.verifiedSpecs ?: "")
                put("editInfo", e.editInfo)
                put("localThumbPath", e.localThumbPath ?: "")
                put("sourceUrl", e.sourceUrl)
                put("timestampMillis", e.timestampMillis)
            })
        }
        prefs.edit().putString("my_edits_json", arr.toString()).apply()
    }

    private fun loadMyEditsFromPrefs(): List<MyEditRecord> = runCatching {
        val raw = prefs.getString("my_edits_json", null) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            MyEditRecord(
                id = o.optString("id"),
                title = o.optString("title"),
                filePath = o.optString("filePath"),
                fileName = o.optString("fileName"),
                fileSizeBytes = o.optLong("fileSizeBytes"),
                verifiedSpecs = o.optString("verifiedSpecs").ifBlank { null },
                editInfo = o.optString("editInfo", "Edited"),
                localThumbPath = o.optString("localThumbPath").ifBlank { null },
                sourceUrl = o.optString("sourceUrl"),
                timestampMillis = o.optLong("timestampMillis", System.currentTimeMillis())
            )
        }
    }.getOrDefault(emptyList())

    fun editAndSaveVideo(
        record: SavedMediaRecord,
        startMs: Long,
        endMs: Long,
        cropX: Float?,
        cropY: Float?,
        cropW: Float?,
        cropH: Float?,
        editLabel: String,
        onDone: (Boolean, String) -> Unit
    ) {
        if (_uiState.value.isEditingVideo) return
        val vaultFile = record.vaultPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
        val inputUri = if (vaultFile != null) Uri.fromFile(vaultFile) else record.fileUri
        viewModelScope.launch {
            _uiState.update { it.copy(isEditingVideo = true) }
            val result = runCatching {
                val app = getApplication<Application>()
                val editDir = File(app.filesDir, "edits").apply { mkdirs() }
                val outName = "${record.fileName.substringBeforeLast('.')}_edit_${System.currentTimeMillis() % 10000}.mp4"
                val outFile = File(editDir, outName)
                VideoEditorRepository.editVideo(
                    context = app,
                    inputUri = inputUri,
                    outputFile = outFile,
                    startMs = startMs,
                    endMs = endMs,
                    cropX = cropX,
                    cropY = cropY,
                    cropW = cropW,
                    cropH = cropH
                )
                val specs = withContext(Dispatchers.IO) { VideoSpecsVerifier.inspectVideoFile(outFile) }
                val thumbPath = VideoEditorRepository.extractThumbFile(
                    outFile,
                    File(File(app.filesDir, "thumbs"), "${outFile.nameWithoutExtension}_thumb.jpg")
                )
                MyEditRecord(
                    id = UUID.randomUUID().toString(),
                    title = record.title,
                    filePath = outFile.absolutePath,
                    fileName = outName,
                    fileSizeBytes = outFile.length(),
                    verifiedSpecs = specs,
                    editInfo = editLabel,
                    localThumbPath = thumbPath ?: record.localThumbPath,
                    sourceUrl = record.sourceUrl
                )
            }
            result.onSuccess { rec ->
                _uiState.update { state ->
                    val updated = listOf(rec) + state.myEdits
                    saveMyEditsToPrefs(updated)
                    state.copy(isEditingVideo = false, myEdits = updated)
                }
                onDone(true, "Hasil edit disimpan ke My Edit!")
            }.onFailure { err ->
                _uiState.update { it.copy(isEditingVideo = false) }
                onDone(false, err.message ?: "Gagal menyimpan edit.")
            }
        }
    }

    fun saveEditToGallery(edit: MyEditRecord, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(edit.filePath)
            if (!file.exists()) {
                withContext(Dispatchers.Main) { onResult("File edit tidak ditemukan.") }
                return@launch
            }
            val res = runCatching {
                downloader.saveToMediaStore(
                    sourceFile = file,
                    displayName = edit.fileName,
                    mimeType = "video/mp4",
                    kind = DownloadKind.VIDEO_HD
                )
            }
            withContext(Dispatchers.Main) {
                if (res.isSuccess) onResult("Disimpan ke Galeri (DCIM/Camera)!")
                else onResult("Gagal menyimpan ke Galeri.")
            }
        }
    }

    fun removeEditItem(id: String) {
        _uiState.update { state ->
            state.myEdits.find { it.id == id }?.let { runCatching { File(it.filePath).delete() } }
            val updated = state.myEdits.filterNot { it.id == id }
            saveMyEditsToPrefs(updated)
            state.copy(myEdits = updated)
        }
    }

    fun clearAllEdits() {
        _uiState.update { state ->
            state.myEdits.forEach { runCatching { File(it.filePath).delete() } }
            runCatching { File(getApplication<Application>().filesDir, "edits").deleteRecursively() }
            saveMyEditsToPrefs(emptyList())
            state.copy(myEdits = emptyList())
        }
    }

    fun prepareWhatsAppHd(edit: MyEditRecord, onReady: (File?) -> Unit) {
        if (_uiState.value.isPreparingWaHd) return
        val src = File(edit.filePath)
        if (!src.exists()) {
            onReady(null)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isPreparingWaHd = true) }
            val outFile = File(getApplication<Application>().cacheDir, "wa_status_hd.mp4")
            val result = runCatching {
                VideoEditorRepository.prepareWhatsAppHdStatus(getApplication(), src, outFile)
            }.getOrNull()
            _uiState.update { it.copy(isPreparingWaHd = false) }
            onReady(result ?: src)
        }
    }

    fun checkApkUpdate(currentVersion: String, onMessage: (String) -> Unit) {
        if (_uiState.value.isCheckingApkUpdate) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingApkUpdate = true) }
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val client = OkHttpClient()
                    val req = Request.Builder()
                        .url("https://api.github.com/repos/adhitaja222-droid/ElyReneDownloader/releases/latest")
                        .header("User-Agent", "ElyReneDownloader")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        val obj = JSONObject(resp.body?.string().orEmpty())
                        val tag = obj.optString("tag_name").trimStart('v', 'V').trim()
                        val rawBody = if (obj.isNull("body")) "" else obj.optString("body").trim()
                        val notes = rawBody.ifBlank {
                            "Perubahan Update :\nQoL :\n1. memperkuat Status WA HD\n2. menambahkan MB di bar update\n3. Perubahan nama Update Sekarang jadi Update\n4. menambahkan info new update"
                        }
                        val assets = obj.optJSONArray("assets")
                        var apkUrl = ""
                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val a = assets.optJSONObject(i) ?: continue
                                val name = a.optString("name")
                                if (name.endsWith(".apk", ignoreCase = true)) {
                                    apkUrl = a.optString("browser_download_url")
                                    break
                                }
                            }
                        }
                        Triple(tag, notes, apkUrl)
                    }
                }.getOrNull()
            }
            _uiState.update { it.copy(isCheckingApkUpdate = false) }
            if (res != null && res.first.isNotBlank() && res.first != currentVersion && res.third.isNotBlank()) {
                _uiState.update {
                    it.copy(
                        availableUpdateVersion = res.first,
                        availableUpdateNotes = res.second,
                        availableUpdateApkUrl = res.third,
                        downloadedApkFile = null
                    )
                }
            } else {
                onMessage("Aplikasi sudah menggunakan versi terbaru (V $currentVersion).")
            }
        }
    }

    fun dismissApkUpdate() {
        _uiState.update {
            it.copy(
                availableUpdateVersion = null,
                availableUpdateNotes = null,
                availableUpdateApkUrl = null,
                isDownloadingApkUpdate = false,
                apkDownloadProgress = 0f,
                apkDownloadedBytes = 0L,
                apkTotalBytes = 0L,
                downloadedApkFile = null
            )
        }
    }

    fun startApkUpdateDownload() {
        val url = _uiState.value.availableUpdateApkUrl ?: return
        if (_uiState.value.isDownloadingApkUpdate) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloadingApkUpdate = true,
                    apkDownloadProgress = 0f,
                    apkDownloadedBytes = 0L,
                    apkTotalBytes = 0L
                )
            }
            val outFile = withContext(Dispatchers.IO) {
                runCatching {
                    val dest = File(getApplication<Application>().cacheDir, "elyrene_update.apk")
                    val client = OkHttpClient.Builder().followRedirects(true).build()
                    val req = Request.Builder().url(url).build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                        val body = resp.body ?: throw IllegalStateException("Empty body")
                        val total = body.contentLength()
                        _uiState.update { it.copy(apkTotalBytes = total.coerceAtLeast(0L)) }
                        body.byteStream().use { input ->
                            FileOutputStream(dest).use { out ->
                                val buf = ByteArray(16 * 1024)
                                var readSum = 0L
                                var lastUiEmit = 0L
                                while (true) {
                                    val r = input.read(buf)
                                    if (r == -1) break
                                    out.write(buf, 0, r)
                                    readSum += r
                                    if (readSum - lastUiEmit >= 64 * 1024 || readSum == total) {
                                        lastUiEmit = readSum
                                        val pct = if (total > 0) (readSum * 100f / total).coerceIn(0f, 100f) else 0f
                                        _uiState.update {
                                            it.copy(
                                                apkDownloadProgress = pct,
                                                apkDownloadedBytes = readSum,
                                                apkTotalBytes = total.coerceAtLeast(0L)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    dest
                }.getOrNull()
            }
            _uiState.update {
                it.copy(
                    isDownloadingApkUpdate = false,
                    apkDownloadProgress = if (outFile != null) 100f else 0f,
                    apkDownloadedBytes = outFile?.length() ?: it.apkDownloadedBytes,
                    downloadedApkFile = outFile
                )
            }
        }
    }

    fun onUrlChanged(newUrl: String) {
        _uiState.update { it.copy(urlInput = newUrl, errorMessage = null) }
    }

    fun handleSharedText(sharedText: String) {
        val extracted = UrlUtils.extractSupportedUrl(sharedText) ?: sharedText.trim()
        _uiState.update { it.copy(urlInput = extracted, errorMessage = null) }
        if (UrlUtils.extractSupportedUrl(extracted) != null) {
            analyzeCurrentUrl(extracted)
        }
    }

    fun analyzeCurrentUrl(overrideUrl: String? = null) {
        val target = (overrideUrl ?: _uiState.value.urlInput).trim()
        if (target.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Tempel link video atau foto TikTok / Instagram terlebih dahulu.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAnalyzing = true,
                    errorMessage = null,
                    mediaContent = null,
                    downloadedPhotoIndices = emptySet(),
                    lastSuccessBanner = null
                )
            }
            val result = extractor.extractMedia(target)
            result.onSuccess { media ->
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        mediaContent = media,
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        errorMessage = err.message ?: "Gagal menganalisis tautan."
                    )
                }
            }
        }
    }

    fun downloadVideo(option: VideoQualityOption) {
        val media = _uiState.value.mediaContent ?: return
        if (_uiState.value.isDownloading) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloading = true,
                    downloadProgress = 0f,
                    downloadStatus = "Memulai unduhan ${option.label}...",
                    errorMessage = null,
                    lastSuccessBanner = null
                )
            }

            val result = downloader.downloadVideo(media, option) { pct, status ->
                _uiState.update {
                    it.copy(downloadProgress = pct, downloadStatus = status)
                }
            }

            result.onSuccess { record ->
                val specs = record.verifiedSpecs?.let { " ($it)" }.orEmpty()
                appendHistory(listOf(record))
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = 100f,
                        downloadStatus = "",
                        lastSuccessBanner = "Berhasil disimpan ke Galeri: ${record.fileName}$specs"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadStatus = "",
                        errorMessage = err.message ?: "Gagal mengunduh video."
                    )
                }
            }
        }
    }

    fun downloadMp3() {
        val media = _uiState.value.mediaContent ?: return
        if (_uiState.value.isDownloading) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloading = true,
                    downloadProgress = 0f,
                    downloadStatus = "Memulai ekstraksi MP3...",
                    errorMessage = null,
                    lastSuccessBanner = null
                )
            }

            val result = downloader.downloadMp3(media) { pct, status ->
                _uiState.update {
                    it.copy(downloadProgress = pct, downloadStatus = status)
                }
            }

            result.onSuccess { record ->
                appendHistory(listOf(record))
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = 100f,
                        downloadStatus = "",
                        lastSuccessBanner = "Audio MP3 disimpan ke Music/ElyReneDownloader: ${record.fileName}"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadStatus = "",
                        errorMessage = err.message ?: "Gagal mengunduh MP3."
                    )
                }
            }
        }
    }

    fun downloadPhoto(photo: PhotoSlideItem) {
        val media = _uiState.value.mediaContent ?: return
        if (_uiState.value.downloadingPhotoIndex != null || _uiState.value.isDownloading) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    downloadingPhotoIndex = photo.index,
                    errorMessage = null,
                    lastSuccessBanner = null
                )
            }

            val result = downloader.downloadPhotoSlide(media, photo) { _ -> }
            result.onSuccess { record ->
                appendHistory(listOf(record))
                _uiState.update {
                    it.copy(
                        downloadingPhotoIndex = null,
                        downloadedPhotoIndices = it.downloadedPhotoIndices + photo.index,
                        lastSuccessBanner = "Foto slide #${photo.index} disimpan ke Pictures/ElyReneDownloader"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        downloadingPhotoIndex = null,
                        errorMessage = err.message ?: "Gagal mengunduh foto slide #${photo.index}."
                    )
                }
            }
        }
    }

    fun downloadAllPhotos() {
        val media = _uiState.value.mediaContent ?: return
        if (media.photos.isEmpty() || _uiState.value.isDownloading) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloading = true,
                    downloadProgress = 0f,
                    downloadStatus = "Mengunduh semua ${media.photos.size} foto...",
                    errorMessage = null,
                    lastSuccessBanner = null
                )
            }

            val newRecords = mutableListOf<SavedMediaRecord>()
            val doneIndices = _uiState.value.downloadedPhotoIndices.toMutableSet()

            media.photos.forEachIndexed { i, photo ->
                _uiState.update {
                    it.copy(
                        downloadingPhotoIndex = photo.index,
                        downloadProgress = ((i.toFloat()) / media.photos.size) * 100f,
                        downloadStatus = "Mengunduh foto ${i + 1} dari ${media.photos.size}..."
                    )
                }
                val res = downloader.downloadPhotoSlide(media, photo) {}
                res.onSuccess { rec ->
                    newRecords.add(rec)
                    doneIndices.add(photo.index)
                }
            }

            if (newRecords.isNotEmpty()) {
                appendHistory(newRecords.reversed())
            }
            _uiState.update {
                it.copy(
                    isDownloading = false,
                    downloadingPhotoIndex = null,
                    downloadProgress = 100f,
                    downloadStatus = "",
                    downloadedPhotoIndices = doneIndices,
                    lastSuccessBanner = "${newRecords.size}/${media.photos.size} foto berhasil disimpan ke Galeri!"
                )
            }
        }
    }

    fun saveInstagramCookiesFromWebView(cookieHeader: String) {
        cookieStorage.saveFromWebViewCookieHeader(cookieHeader)
        _uiState.update {
            it.copy(
                hasInstagramCookies = cookieStorage.hasInstagramCookies(),
                engineUpdateMessage = "Sesi login Instagram tersimpan!"
            )
        }
    }

    fun clearInstagramCookies() {
        cookieStorage.clearCookies()
        _uiState.update {
            it.copy(
                hasInstagramCookies = false,
                engineUpdateMessage = "Sesi login Instagram dihapus."
            )
        }
    }

    fun updateYtDlpEngine() {
        if (_uiState.value.isUpdatingYtDlp) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isUpdatingYtDlp = true, engineUpdateMessage = "Memeriksa pembaruan yt-dlp...")
            }
            val msg = withContext(Dispatchers.IO) {
                runCatching {
                    val status = YoutubeDL.getInstance().updateYoutubeDL(
                        getApplication(),
                        YoutubeDL.UpdateChannel.STABLE
                    )
                    when (status) {
                        YoutubeDL.UpdateStatus.DONE -> "Engine yt-dlp berhasil diperbarui ke versi terbaru!"
                        YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Engine yt-dlp sudah versi paling baru."
                        else -> "Status update: $status"
                    }
                }.getOrElse { e ->
                    "Gagal update yt-dlp: ${e.message}"
                }
            }
            _uiState.update {
                it.copy(isUpdatingYtDlp = false, engineUpdateMessage = msg)
            }
        }
    }

    fun retryEngineInit() {
        (getApplication<Application>() as TikIGApp).initEngine()
    }

    fun dismissBanner() {
        _uiState.update { it.copy(lastSuccessBanner = null, errorMessage = null, engineUpdateMessage = null) }
    }
}
