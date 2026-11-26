package com.tikigsaver.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.tikigsaver.app.BuildConfig
import com.tikigsaver.app.EngineInitState
import com.tikigsaver.app.R
import com.tikigsaver.app.engine.VideoEditorRepository
import com.tikigsaver.app.model.DownloadKind
import com.tikigsaver.app.model.MyEditRecord
import com.tikigsaver.app.model.SavedMediaRecord
import com.tikigsaver.app.model.formatBytes
import com.tikigsaver.app.ui.components.InstagramCookieLoginDialog
import com.tikigsaver.app.ui.components.MediaResultSection
import com.tikigsaver.app.ui.theme.CardBorder
import com.tikigsaver.app.ui.theme.EmeraldBright
import com.tikigsaver.app.ui.theme.EmeraldPrimary
import com.tikigsaver.app.ui.theme.SlateDark
import com.tikigsaver.app.ui.theme.SlateMuted
import com.tikigsaver.app.ui.theme.SurfaceBg
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TikIGSaverApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val engineState by viewModel.engineState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showIgLoginDialog by remember { mutableStateOf(false) }
    var showSplash by remember { mutableStateOf(true) }
    var editingRecord by remember { mutableStateOf<SavedMediaRecord?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(1200)
        showSplash = false
    }

    // APK update dialog
    if (uiState.availableUpdateVersion != null) {
        ApkUpdateDialog(
            version = uiState.availableUpdateVersion!!,
            notes = uiState.availableUpdateNotes.orEmpty(),
            isDownloading = uiState.isDownloadingApkUpdate,
            progress = uiState.apkDownloadProgress,
            downloadedApkFile = uiState.downloadedApkFile,
            onUpdate = viewModel::startApkUpdateDownload,
            onDismiss = viewModel::dismissApkUpdate
        )
    }

    // Edit video dialog
    editingRecord?.let { record ->
        EditVideoDialog(
            record = record,
            uiState = uiState,
            onSave = { startMs, endMs, cropX, cropY, cropW, cropH, label ->
                viewModel.editAndSaveVideo(
                    record, startMs, endMs, cropX, cropY, cropW, cropH, label
                ) { ok, msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                    if (ok) editingRecord = null
                }
            },
            onDismiss = { editingRecord = null }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = SurfaceBg,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_elyrene_logo),
                                contentDescription = "ElyReneDownloader Logo",
                                modifier = Modifier.size(34.dp),
                                contentScale = ContentScale.Fit
                            )
                            Text(
                                text = "ElyReneDownloader",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 19.sp,
                                color = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateDark)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                        label = { Text("Downloader") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                        label = { Text("Riwayat (${uiState.savedHistory.size})") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        label = { Text("My Edit (${uiState.myEdits.size})") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        label = { Text("Pengaturan") }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    0 -> DownloaderTab(
                        uiState = uiState,
                        engineState = engineState,
                        viewModel = viewModel
                    )
                    1 -> HistoryTab(
                        history = uiState.savedHistory,
                        onCopyLink = { url ->
                            scope.launch {
                                snackbarHostState.showSnackbar("Link berhasil disalin!")
                            }
                        },
                        onReDownload = { record ->
                            viewModel.reDownloadFromHistory(record)
                            selectedTab = 0
                        },
                        onDeleteItem = viewModel::removeHistoryItem,
                        onClearAll = viewModel::clearAllHistory,
                        onEditItem = { rec -> editingRecord = rec }
                    )
                    2 -> MyEditTab(
                        edits = uiState.myEdits,
                        onSaveToGallery = { edit ->
                            viewModel.saveEditToGallery(edit) { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        },
                        onDelete = viewModel::removeEditItem,
                        onClearAll = viewModel::clearAllEdits
                    )
                    3 -> SettingsTab(
                        uiState = uiState,
                        engineState = engineState,
                        onUpdateYtDlp = viewModel::updateYtDlpEngine,
                        onRetryInit = viewModel::retryEngineInit,
                        onCheckUpdate = {
                            viewModel.checkApkUpdate(BuildConfig.VERSION_NAME) { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        }
                    )
                }
            }
        }

        if (showIgLoginDialog) {
            InstagramCookieLoginDialog(
                onSaveCookies = { cookieHeader ->
                    viewModel.saveInstagramCookiesFromWebView(cookieHeader)
                    showIgLoginDialog = false
                },
                onDismiss = { showIgLoginDialog = false }
            )
        }

        AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_elyrene_logo),
                        contentDescription = "ElyReneDownloader",
                        modifier = Modifier.size(120.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        text = "ElyReneDownloader",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloaderTab(
    uiState: MainUiState,
    engineState: EngineInitState,
    viewModel: MainViewModel
) {
    val clipboard = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (engineState is EngineInitState.Loading) {
            StatusBanner(
                text = "Menyiapkan engine on-device (yt-dlp & FFmpeg)...",
                isError = false,
                isSuccess = false
            )
        } else if (engineState is EngineInitState.Error) {
            StatusBanner(
                text = "Engine error: ${engineState.message}",
                isError = true,
                isSuccess = false,
                actionLabel = "Coba Lagi",
                onAction = viewModel::retryEngineInit
            )
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Tempel tautan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = SlateDark
                )

                OutlinedTextField(
                    value = uiState.urlInput,
                    onValueChange = viewModel::onUrlChanged,
                    placeholder = { Text("https://vt.tiktok.com/... atau https://instagram.com/...") },
                    leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.urlInput.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onUrlChanged("") }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Hapus")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipText = clipboard.getText()?.text.orEmpty()
                            if (clipText.isNotBlank()) {
                                viewModel.handleSharedText(clipText)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tempel", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { viewModel.analyzeCurrentUrl() },
                        enabled = !uiState.isAnalyzing && engineState !is EngineInitState.Loading,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        if (uiState.isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Memproses...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        uiState.errorMessage?.let { err ->
            StatusBanner(
                text = err,
                isError = true,
                isSuccess = false,
                actionLabel = "Tutup",
                onAction = viewModel::dismissBanner
            )
        }

        uiState.lastSuccessBanner?.let { ok ->
            StatusBanner(
                text = ok,
                isError = false,
                isSuccess = true,
                actionLabel = "Tutup",
                onAction = viewModel::dismissBanner
            )
        }

        uiState.mediaContent?.let { media ->
            MediaResultSection(
                media = media,
                isDownloading = uiState.isDownloading,
                downloadProgress = uiState.downloadProgress,
                downloadStatus = uiState.downloadStatus,
                downloadingPhotoIndex = uiState.downloadingPhotoIndex,
                downloadedPhotoIndices = uiState.downloadedPhotoIndices,
                onDownloadVideoHd = viewModel::downloadVideo,
                onDownloadVideoNormal = viewModel::downloadVideo,
                onDownloadMp3 = viewModel::downloadMp3,
                onDownloadPhoto = viewModel::downloadPhoto,
                onDownloadAllPhotos = viewModel::downloadAllPhotos
            )
        } ?: run {
            if (!uiState.isAnalyzing) {
                EmptyTipCard()
            }
        }
    }
}

@Composable
private fun EmptyTipCard() {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Fitur Utama",
                fontWeight = FontWeight.Bold,
                color = SlateDark,
                fontSize = 14.sp
            )
            Text(
                text = "• Video HD Resolusi & FPS Asli (60 FPS tetap 60 FPS, tidak turun ke 30 FPS)\n" +
                    "• Video Normal untuk ukuran file lebih ringan\n" +
                    "• Download MP3 saja langsung dari video/slideshow\n" +
                    "• Mode Foto/Slide TikTok & Carousel IG: tampil grid foto untuk pilih gambar mana yang ingin diunduh\n" +
                    "• Bisa langsung Share dari aplikasi TikTok / Instagram ke TikIG Saver",
                color = SlateMuted,
                fontSize = 12.5.sp,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun HistoryTab(
    history: List<SavedMediaRecord>,
    onCopyLink: (String) -> Unit,
    onReDownload: (SavedMediaRecord) -> Unit,
    onDeleteItem: (String) -> Unit,
    onClearAll: () -> Unit,
    onEditItem: (SavedMediaRecord) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var activeMedia by remember { mutableStateOf<SavedMediaRecord?>(null) }

    activeMedia?.let { item ->
        InAppMediaPlayerDialog(
            item = item,
            onDismiss = { activeMedia = null }
        )
    }

    if (history.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Belum ada riwayat unduhan.\nFile tersimpan di Galeri & salinan offline di aplikasi.",
                color = SlateMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Riwayat Unduhan",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SlateDark
                )
                Button(
                    onClick = onClearAll,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Hapus Semua", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }

        items(history, key = { it.id }) { item ->
            var menuExpanded by remember { mutableStateOf(false) }

            fun openMedia() {
                activeMedia = item
            }

            val isVideo = item.kind == DownloadKind.VIDEO_HD ||
                item.kind == DownloadKind.VIDEO_NORMAL ||
                item.kind == DownloadKind.VIDEO_2K

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.clickable { openMedia() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val icon = when (item.kind) {
                        DownloadKind.VIDEO_2K, DownloadKind.VIDEO_HD, DownloadKind.VIDEO_NORMAL -> Icons.Rounded.VideoFile
                        DownloadKind.MP3 -> Icons.Rounded.MusicNote
                        DownloadKind.IMAGE -> Icons.Rounded.Image
                    }
                    val thumbModel: Any? = remember(item.id) {
                        item.localThumbPath?.let { File(it) }?.takeIf { it.exists() }
                            ?: item.thumbnailUrl?.takeIf { it.isNotBlank() }
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(EmeraldBright.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumbModel != null) {
                            AsyncImage(
                                model = thumbModel,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(icon, contentDescription = null, tint = EmeraldPrimary)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title.ifBlank { item.fileName },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = SlateDark,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        val meta = buildString {
                            append(item.kind.label)
                            if (!item.verifiedSpecs.isNullOrBlank()) {
                                append(" • ")
                                append(item.verifiedSpecs)
                            }
                            if (item.fileSizeBytes > 0L) {
                                append(" • ")
                                append(formatBytes(item.fileSizeBytes))
                            }
                        }
                        Text(
                            text = meta,
                            fontSize = 11.5.sp,
                            color = SlateMuted,
                            maxLines = 2
                        )
                        Text(
                            text = formatRelativeDate(item.timestampMillis),
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Opsi", tint = SlateMuted)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            if (isVideo) {
                                DropdownMenuItem(
                                    text = { Text("Edit Video") },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onEditItem(item)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Bagikan") },
                                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    val mime = when (item.kind) {
                                        DownloadKind.VIDEO_2K, DownloadKind.VIDEO_HD, DownloadKind.VIDEO_NORMAL -> "video/*"
                                        DownloadKind.MP3 -> "audio/*"
                                        DownloadKind.IMAGE -> "image/*"
                                    }
                                    val vaultFile = item.vaultPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
                                    val shareUri = if (vaultFile != null) {
                                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", vaultFile)
                                    } else {
                                        item.fileUri
                                    }
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = mime
                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching {
                                        context.startActivity(Intent.createChooser(shareIntent, "Bagikan ke"))
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy Link") },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    if (item.sourceUrl.isNotBlank()) {
                                        clipboard.setText(AnnotatedString(item.sourceUrl))
                                        onCopyLink(item.sourceUrl)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Download Lagi") },
                                leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onReDownload(item)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Hapus dari Riwayat", color = Color(0xFFDC2626)) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color(0xFFDC2626)) },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteItem(item.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatRelativeDate(ts: Long): String {
    val diffSec = (System.currentTimeMillis() - ts) / 1000L
    return when {
        diffSec < 60 -> "Baru saja"
        diffSec < 3600 -> "${diffSec / 60} menit lalu"
        diffSec < 86400 -> "${diffSec / 3600} jam lalu"
        else -> SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("id", "ID")).format(Date(ts))
    }
}

@Composable
private fun InAppMediaPlayerDialog(
    item: SavedMediaRecord,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val vaultFile = remember(item.id) {
        item.vaultPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
    }
    val targetUri: Uri = remember(item.id) {
        if (vaultFile != null) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", vaultFile)
        } else {
            item.fileUri
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            when (item.kind) {
                DownloadKind.IMAGE -> {
                    AsyncImage(
                        model = vaultFile ?: item.fileUri,
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                DownloadKind.MP3 -> {
                    var isPlaying by remember { mutableStateOf(true) }
                    val player = remember(item.id) {
                        MediaPlayer().apply {
                            runCatching {
                                setDataSource(context, targetUri)
                                prepare()
                                start()
                                setOnCompletionListener { isPlaying = false }
                            }
                        }
                    }
                    DisposableEffect(item.id) {
                        onDispose {
                            runCatching {
                                if (player.isPlaying) player.stop()
                                player.release()
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!item.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = item.thumbnailUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                        }
                        Text(
                            text = item.title.ifBlank { item.fileName },
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Button(
                            onClick = {
                                if (player.isPlaying) {
                                    player.pause()
                                    isPlaying = false
                                } else {
                                    player.start()
                                    isPlaying = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isPlaying) "Jeda Audio" else "Putar Audio", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                else -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val controller = MediaController(ctx)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                setVideoURI(targetUri)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                            }
                        }
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Tutup", tint = Color.White)
            }
        }
    }
}

// ─── MY EDIT TAB ─────────────────────────────────────────────────────────────

@Composable
private fun MyEditTab(
    edits: List<MyEditRecord>,
    onSaveToGallery: (MyEditRecord) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current

    if (edits.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Belum ada hasil edit.\nEdit video dari tab Riwayat lalu simpan.",
                color = SlateMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Edit",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SlateDark
                )
                Button(
                    onClick = onClearAll,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Hapus Semua", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }

        items(edits, key = { it.id }) { edit ->
            var menuExpanded by remember { mutableStateOf(false) }
            val editFile = remember(edit.id) { File(edit.filePath) }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val thumbModel: Any? = remember(edit.id) {
                        edit.localThumbPath?.let { File(it) }?.takeIf { it.exists() }
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 68.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(EmeraldBright.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumbModel != null) {
                            AsyncImage(
                                model = thumbModel,
                                contentDescription = edit.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Rounded.VideoFile, contentDescription = null, tint = EmeraldPrimary)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = edit.title.ifBlank { edit.fileName },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = SlateDark,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        val meta = buildString {
                            append(edit.editInfo)
                            if (!edit.verifiedSpecs.isNullOrBlank()) {
                                append(" • ")
                                append(edit.verifiedSpecs)
                            }
                            if (edit.fileSizeBytes > 0L) {
                                append(" • ")
                                append(formatBytes(edit.fileSizeBytes))
                            }
                        }
                        Text(text = meta, fontSize = 11.5.sp, color = SlateMuted, maxLines = 2)
                        Text(
                            text = formatRelativeDate(edit.timestampMillis),
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Opsi", tint = SlateMuted)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Simpan ke Galeri") },
                                leadingIcon = { Icon(Icons.Rounded.SaveAlt, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onSaveToGallery(edit)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Bagikan") },
                                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    if (editFile.exists()) {
                                        val uri = FileProvider.getUriForFile(
                                            context, "${context.packageName}.fileprovider", editFile
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "video/*"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching {
                                            context.startActivity(Intent.createChooser(intent, "Bagikan ke"))
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Status WA (HD)", color = Color(0xFF25D366)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Share,
                                        contentDescription = null,
                                        tint = Color(0xFF25D366)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    if (editFile.exists()) {
                                        val uri = FileProvider.getUriForFile(
                                            context, "${context.packageName}.fileprovider", editFile
                                        )
                                        val waIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "video/*"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            setPackage("com.whatsapp")
                                        }
                                        runCatching {
                                            context.startActivity(waIntent)
                                        }.onFailure {
                                            runCatching {
                                                context.startActivity(
                                                    Intent.createChooser(
                                                        Intent(Intent.ACTION_SEND).apply {
                                                            type = "video/*"
                                                            putExtra(Intent.EXTRA_STREAM, uri)
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        },
                                                        "Kirim sebagai Status WA"
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Hapus", color = Color(0xFFDC2626)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = null,
                                        tint = Color(0xFFDC2626)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDelete(edit.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── SETTINGS TAB ────────────────────────────────────────────────────────────

@Composable
private fun SettingsTab(
    uiState: MainUiState,
    engineState: EngineInitState,
    onUpdateYtDlp: () -> Unit,
    onRetryInit: () -> Unit,
    onCheckUpdate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── VERSI ──────────────────────────────────────────────────────────
        Text(
            "Versi",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            color = SlateDark
        )
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "ElyReneDownloader",
                            fontWeight = FontWeight.Bold,
                            color = SlateDark
                        )
                        Text(
                            "V ${BuildConfig.VERSION_NAME}",
                            fontSize = 13.sp,
                            color = SlateMuted
                        )
                    }
                    Button(
                        onClick = onCheckUpdate,
                        enabled = !uiState.isCheckingApkUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (uiState.isCheckingApkUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Memeriksa...", fontSize = 12.sp)
                        } else {
                            Icon(
                                Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "CEK UPDATE APK",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── INFORMASI ──────────────────────────────────────────────────────
        Text(
            "Informasi",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            color = SlateDark
        )
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Engine Downloader (yt-dlp + FFmpeg)",
                    fontWeight = FontWeight.Bold,
                    color = SlateDark
                )
                val statusLabel = when (engineState) {
                    EngineInitState.Loading -> "Sedang inisialisasi..."
                    EngineInitState.Ready -> "✅ Aktif & Siap (On-Device)"
                    is EngineInitState.Error -> "❌ Error: ${engineState.message}"
                }
                Text(statusLabel, fontSize = 12.5.sp, color = SlateMuted)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onUpdateYtDlp,
                        enabled = !uiState.isUpdatingYtDlp && engineState is EngineInitState.Ready,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (uiState.isUpdatingYtDlp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Update yt-dlp", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }

                    if (engineState is EngineInitState.Error) {
                        OutlinedButton(onClick = onRetryInit, shape = RoundedCornerShape(8.dp)) {
                            Text("Inisialisasi Ulang", fontSize = 12.5.sp)
                        }
                    }
                }

                uiState.engineUpdateMessage?.let { msg ->
                    Text(
                        msg,
                        fontSize = 12.sp,
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Lokasi Penyimpanan", fontWeight = FontWeight.Bold, color = SlateDark)
                Text(
                    text = "• Video: DCIM/Camera\n" +
                        "• Foto / Slide: DCIM/Camera\n" +
                        "• Audio MP3: Music/ElyReneDownloader",
                    fontSize = 12.5.sp,
                    color = SlateMuted,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

// ─── EDIT VIDEO DIALOG ───────────────────────────────────────────────────────

@Composable
private fun EditVideoDialog(
    record: SavedMediaRecord,
    uiState: MainUiState,
    onSave: (startMs: Long, endMs: Long, cropX: Float?, cropY: Float?, cropW: Float?, cropH: Float?, label: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val vaultFile = remember(record.id) {
        record.vaultPath?.let { File(it) }?.takeIf { it.exists() }
    }
    val inputUri = remember(record.id) {
        if (vaultFile != null) Uri.fromFile(vaultFile) else record.fileUri
    }

    var durationMs by remember { mutableStateOf(0L) }
    var startMs by remember { mutableStateOf(0L) }
    var endMs by remember { mutableStateOf(0L) }

    var cropMode by remember { mutableStateOf("Asli") }
    var cropX by remember { mutableFloatStateOf(0f) }
    var cropY by remember { mutableFloatStateOf(0f) }
    var cropW by remember { mutableFloatStateOf(1f) }
    var cropH by remember { mutableFloatStateOf(1f) }

    var frames by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    LaunchedEffect(record.id) {
        runCatching {
            val mmr = MediaMetadataRetriever()
            if (vaultFile != null) mmr.setDataSource(vaultFile.absolutePath)
            else mmr.setDataSource(context, inputUri)
            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            mmr.release()
            durationMs = dur
            endMs = dur
        }
        frames = VideoEditorRepository.extractTimelineFrames(inputUri, context, 10)
    }

    val thumbModel: Any? = remember(record.id) {
        record.localThumbPath?.let { File(it) }?.takeIf { it.exists() }
            ?: record.thumbnailUrl?.takeIf { it.isNotBlank() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // ── Top bar ───────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A))
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Tutup", tint = Color.White)
                }
                Text(
                    "Edit Video",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Button(
                    onClick = {
                        val label = if (cropMode == "Asli") "Edit Trim" else "Edit $cropMode Crop"
                        onSave(
                            startMs,
                            if (endMs > 0 && endMs < durationMs) endMs else durationMs,
                            if (cropMode == "Asli") null else cropX,
                            if (cropMode == "Asli") null else cropY,
                            if (cropMode == "Asli") null else cropW,
                            if (cropMode == "Asli") null else cropH,
                            label
                        )
                    },
                    enabled = !uiState.isEditingVideo,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (uiState.isEditingVideo) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Menyimpan...", fontSize = 11.sp)
                    } else {
                        Text("SIMPAN EDIT", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            // ── Video preview + crop overlay ──────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0A0A0A)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbModel != null) {
                    AsyncImage(
                        model = thumbModel,
                        contentDescription = "Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Rounded.VideoFile,
                        contentDescription = null,
                        tint = Color(0xFF555555),
                        modifier = Modifier.size(72.dp)
                    )
                }

                // Crop overlay — only when cropMode != "Asli"
                if (cropMode != "Asli") {
                    var canvasSize by remember { mutableStateOf(Size.Zero) }
                    var dragHandle by remember { mutableStateOf(-1) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(cropMode) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val w = canvasSize.width
                                        val h = canvasSize.height
                                        if (w <= 0f || h <= 0f) return@detectDragGestures
                                        val left = cropX * w
                                        val top = cropY * h
                                        val right = (cropX + cropW) * w
                                        val bottom = (cropY + cropH) * h
                                        val cx = (left + right) / 2f
                                        val cy = (top + bottom) / 2f
                                        val zone = 50f
                                        dragHandle = when {
                                            abs(offset.x - left) < zone && abs(offset.y - top) < zone -> 0
                                            abs(offset.x - right) < zone && abs(offset.y - top) < zone -> 1
                                            abs(offset.x - left) < zone && abs(offset.y - bottom) < zone -> 2
                                            abs(offset.x - right) < zone && abs(offset.y - bottom) < zone -> 3
                                            abs(offset.x - cx) < zone && abs(offset.y - top) < zone -> 4
                                            abs(offset.x - cx) < zone && abs(offset.y - bottom) < zone -> 5
                                            abs(offset.x - left) < zone && abs(offset.y - cy) < zone -> 6
                                            abs(offset.x - right) < zone && abs(offset.y - cy) < zone -> 7
                                            offset.x in left..right && offset.y in top..bottom -> 8
                                            else -> -1
                                        }
                                    },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        val w = canvasSize.width
                                        val h = canvasSize.height
                                        if (w <= 0f || h <= 0f || dragHandle == -1) return@detectDragGestures
                                        val dx = drag.x / w
                                        val dy = drag.y / h
                                        val minSz = 0.08f
                                        when (dragHandle) {
                                            0 -> { // TL
                                                val nx = (cropX + dx).coerceIn(0f, cropX + cropW - minSz)
                                                val ny = (cropY + dy).coerceIn(0f, cropY + cropH - minSz)
                                                cropW -= nx - cropX; cropH -= ny - cropY
                                                cropX = nx; cropY = ny
                                            }
                                            1 -> { // TR
                                                val ny = (cropY + dy).coerceIn(0f, cropY + cropH - minSz)
                                                cropW = (cropW + dx).coerceIn(minSz, 1f - cropX)
                                                cropH -= ny - cropY; cropY = ny
                                            }
                                            2 -> { // BL
                                                val nx = (cropX + dx).coerceIn(0f, cropX + cropW - minSz)
                                                cropW -= nx - cropX; cropX = nx
                                                cropH = (cropH + dy).coerceIn(minSz, 1f - cropY)
                                            }
                                            3 -> { // BR
                                                cropW = (cropW + dx).coerceIn(minSz, 1f - cropX)
                                                cropH = (cropH + dy).coerceIn(minSz, 1f - cropY)
                                            }
                                            4 -> { // Top edge
                                                val ny = (cropY + dy).coerceIn(0f, cropY + cropH - minSz)
                                                cropH -= ny - cropY; cropY = ny
                                            }
                                            5 -> { // Bottom edge
                                                cropH = (cropH + dy).coerceIn(minSz, 1f - cropY)
                                            }
                                            6 -> { // Left edge
                                                val nx = (cropX + dx).coerceIn(0f, cropX + cropW - minSz)
                                                cropW -= nx - cropX; cropX = nx
                                            }
                                            7 -> { // Right edge
                                                cropW = (cropW + dx).coerceIn(minSz, 1f - cropX)
                                            }
                                            8 -> { // Move
                                                cropX = (cropX + dx).coerceIn(0f, 1f - cropW)
                                                cropY = (cropY + dy).coerceIn(0f, 1f - cropH)
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            canvasSize = size
                            val left = cropX * size.width
                            val top = cropY * size.height
                            val right = (cropX + cropW) * size.width
                            val bottom = (cropY + cropH) * size.height
                            val cw = right - left
                            val ch = bottom - top
                            val cx = (left + right) / 2f
                            val cy = (top + bottom) / 2f

                            // Dim outside crop area
                            val dimColor = Color.Black.copy(alpha = 0.52f)
                            drawRect(dimColor, topLeft = Offset.Zero, size = Size(size.width, top))
                            drawRect(dimColor, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
                            drawRect(dimColor, topLeft = Offset(0f, top), size = Size(left, ch))
                            drawRect(dimColor, topLeft = Offset(right, top), size = Size(size.width - right, ch))

                            // Crop border
                            drawRect(
                                color = Color(0xFF10B981),
                                topLeft = Offset(left, top),
                                size = Size(cw, ch),
                                style = Stroke(width = 3f)
                            )

                            // Corner handles (white L-shapes)
                            val hs = 26f
                            val ht = 5f
                            // TL
                            drawLine(Color.White, Offset(left, top), Offset(left + hs, top), ht, StrokeCap.Round)
                            drawLine(Color.White, Offset(left, top), Offset(left, top + hs), ht, StrokeCap.Round)
                            // TR
                            drawLine(Color.White, Offset(right, top), Offset(right - hs, top), ht, StrokeCap.Round)
                            drawLine(Color.White, Offset(right, top), Offset(right, top + hs), ht, StrokeCap.Round)
                            // BL
                            drawLine(Color.White, Offset(left, bottom), Offset(left + hs, bottom), ht, StrokeCap.Round)
                            drawLine(Color.White, Offset(left, bottom), Offset(left, bottom - hs), ht, StrokeCap.Round)
                            // BR
                            drawLine(Color.White, Offset(right, bottom), Offset(right - hs, bottom), ht, StrokeCap.Round)
                            drawLine(Color.White, Offset(right, bottom), Offset(right, bottom - hs), ht, StrokeCap.Round)

                            // Edge mid handles
                            val hm = 18f
                            drawLine(Color.White, Offset(cx - hm, top), Offset(cx + hm, top), ht, StrokeCap.Round)
                            drawLine(Color.White, Offset(cx - hm, bottom), Offset(cx + hm, bottom), ht, StrokeCap.Round)
                            drawLine(Color.White, Offset(left, cy - hm), Offset(left, cy + hm), ht, StrokeCap.Round)
                            drawLine(Color.White, Offset(right, cy - hm), Offset(right, cy + hm), ht, StrokeCap.Round)
                        }
                    }
                }
            }

            // ── Bottom controls ───────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111111))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Duration label
                Text(
                    "Durasi Video (Geser Garis Kiri / Kanan)",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Mulai: ${"%.1f".format(startMs / 1000f)}s",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                    Text(
                        "Selesai: ${"%.1f".format((if (endMs > 0) endMs else durationMs) / 1000f)}s",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }

                // Filmstrip timeline
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E1E1E))
                ) {
                    if (frames.isNotEmpty()) {
                        LazyRow(modifier = Modifier.fillMaxSize()) {
                            items(frames) { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(42.dp)
                                )
                            }
                        }
                        // Trim handles overlay
                        var filmW by remember { mutableStateOf(0f) }
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(durationMs) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        val w = filmW
                                        if (w <= 0f || durationMs <= 0L) return@detectDragGestures
                                        val tapX = change.position.x
                                        val startX = startMs.toFloat() / durationMs * w
                                        val endX = (if (endMs > 0L) endMs else durationMs).toFloat() / durationMs * w
                                        if (abs(tapX - startX) <= abs(tapX - endX)) {
                                            val newFrac = ((tapX + drag.x) / w).coerceIn(0f, endX / w - 0.01f)
                                            startMs = (newFrac * durationMs).toLong()
                                        } else {
                                            val newFrac = ((tapX + drag.x) / w).coerceIn(startX / w + 0.01f, 1f)
                                            endMs = (newFrac * durationMs).toLong()
                                        }
                                    }
                                }
                        ) {
                            filmW = size.width
                            if (durationMs > 0L) {
                                val sf = startMs.toFloat() / durationMs
                                val ef = (if (endMs > 0L) endMs else durationMs).toFloat() / durationMs
                                val sx = sf * size.width
                                val ex = ef * size.width
                                val dimC = Color.Black.copy(alpha = 0.55f)
                                drawRect(dimC, topLeft = Offset.Zero, size = Size(sx, size.height))
                                drawRect(dimC, topLeft = Offset(ex, 0f), size = Size(size.width - ex, size.height))
                                drawLine(Color.White, Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 6f)
                                drawLine(Color.White, Offset(ex, 0f), Offset(ex, size.height), strokeWidth = 6f)
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LinearProgressIndicator(
                                color = EmeraldPrimary,
                                modifier = Modifier.fillMaxWidth(0.7f)
                            )
                        }
                    }
                }

                // Crop section
                Text(
                    "Ukuran / Potong Video (Geser Kotak di Video)",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                val cropModes = listOf("Asli", "Custom", "1:1", "3:4", "9:16", "16:9")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cropModes) { mode ->
                        FilterChip(
                            selected = cropMode == mode,
                            onClick = {
                                cropMode = mode
                                when (mode) {
                                    "Asli" -> {
                                        cropX = 0f; cropY = 0f; cropW = 1f; cropH = 1f
                                    }
                                    "Custom" -> { /* keep existing crop */ }
                                    "1:1" -> {
                                        val s = minOf(cropW, cropH).coerceAtMost(0.9f)
                                        cropW = s; cropH = s
                                        cropX = ((1f - s) / 2f).coerceIn(0f, 1f - s)
                                        cropY = ((1f - s) / 2f).coerceIn(0f, 1f - s)
                                    }
                                    "3:4" -> {
                                        cropW = 0.75f; cropH = 1f
                                        cropX = 0.125f; cropY = 0f
                                    }
                                    "9:16" -> {
                                        cropW = 0.5625f; cropH = 1f
                                        cropX = 0.21875f; cropY = 0f
                                    }
                                    "16:9" -> {
                                        cropW = 1f; cropH = 0.5625f
                                        cropX = 0f; cropY = 0.21875f
                                    }
                                }
                            },
                            label = { Text(mode, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }
    }
}

// ─── APK UPDATE DIALOG ───────────────────────────────────────────────────────

@Composable
private fun ApkUpdateDialog(
    version: String,
    notes: String,
    isDownloading: Boolean,
    progress: Float,
    downloadedApkFile: File?,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "Update Tersedia!",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = SlateDark
                    )
                }
                Text("Versi V $version siap diinstall.", fontSize = 13.sp, color = SlateMuted)
                if (notes.isNotBlank()) {
                    Text(notes.take(300), fontSize = 12.sp, color = SlateMuted)
                }

                when {
                    isDownloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Mengunduh APK... ${progress.roundToInt()}%",
                                fontSize = 12.sp,
                                color = EmeraldPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = EmeraldPrimary
                            )
                        }
                    }
                    downloadedApkFile != null && downloadedApkFile.exists() -> {
                        Button(
                            onClick = {
                                val apkUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    downloadedApkFile
                                )
                                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                runCatching { context.startActivity(installIntent) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Install / Relog APK", fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Nanti")
                            }
                            Button(
                                onClick = onUpdate,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Update Sekarang", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── STATUS BANNER ───────────────────────────────────────────────────────────

@Composable
private fun StatusBanner(
    text: String,
    isError: Boolean,
    isSuccess: Boolean,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val bg = when {
        isError -> Color(0xFFFEF2F2)
        isSuccess -> Color(0xFFECFDF5)
        else -> Color(0xFFEFF6FF)
    }
    val tint = when {
        isError -> Color(0xFFDC2626)
        isSuccess -> EmeraldPrimary
        else -> Color(0xFF2563EB)
    }
    val icon = when {
        isError -> Icons.Rounded.ErrorOutline
        isSuccess -> Icons.Rounded.CheckCircle
        else -> Icons.Rounded.Download
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Text(
                text = text,
                color = SlateDark,
                fontSize = 12.5.sp,
                modifier = Modifier.weight(1f)
            )
            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(actionLabel, color = tint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
