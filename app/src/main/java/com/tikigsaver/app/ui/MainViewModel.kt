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
import com.tikigsaver.app.model.DownloadKind
import com.tikigsaver.app.model.MediaContent
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
    val hasInstagramCookies: Boolean = false,
    val isUpdatingYtDlp: Boolean = false,
    val engineUpdateMessage: String? = null
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
            savedHistory = loadHistoryFromPrefs()
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
