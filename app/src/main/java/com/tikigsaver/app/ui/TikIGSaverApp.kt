package com.tikigsaver.app.ui

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.tikigsaver.app.EngineInitState
import com.tikigsaver.app.R
import com.tikigsaver.app.model.DownloadKind
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TikIGSaverApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val engineState by viewModel.engineState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showIgLoginDialog by remember { mutableStateOf(false) }
    var showSplash by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(1200)
        showSplash = false
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
                        onClearAll = viewModel::clearAllHistory
                    )
                    2 -> SettingsTab(
                        uiState = uiState,
                        engineState = engineState,
                        onOpenIgLogin = { showIgLoginDialog = true },
                        onClearIgCookies = viewModel::clearInstagramCookies,
                        onUpdateYtDlp = viewModel::updateYtDlpEngine,
                        onRetryInit = viewModel::retryEngineInit
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
    onClearAll: () -> Unit
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
                fontSize = 13.sp
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
                    val icon = when (item.kind) {
                        DownloadKind.VIDEO_2K, DownloadKind.VIDEO_HD, DownloadKind.VIDEO_NORMAL -> Icons.Rounded.VideoFile
                        DownloadKind.MP3 -> Icons.Rounded.MusicNote
                        DownloadKind.IMAGE -> Icons.Rounded.Image
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(EmeraldBright.copy(alpha = 0.14f))
                            .clickable { openMedia() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!item.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = item.thumbnailUrl,
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

@Composable
private fun SettingsTab(
    uiState: MainUiState,
    engineState: EngineInitState,
    onOpenIgLogin: () -> Unit,
    onClearIgCookies: () -> Unit,
    onUpdateYtDlp: () -> Unit,
    onRetryInit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Engine Downloader (yt-dlp + FFmpeg)", fontWeight = FontWeight.Bold, color = SlateDark)
                val statusLabel = when (engineState) {
                    EngineInitState.Loading -> "Sedang inisialisasi..."
                    EngineInitState.Ready -> "Aktif & Siap (On-Device)"
                    is EngineInitState.Error -> "Error: ${engineState.message}"
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
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
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
                    Text(msg, fontSize = 12.sp, color = EmeraldPrimary, fontWeight = FontWeight.Medium)
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Sesi Login Instagram (Cookie)", fontWeight = FontWeight.Bold, color = SlateDark)
                Text(
                    text = if (uiState.hasInstagramCookies) {
                        "Status: Terhubung. Reels HD & Carousel IG yang butuh login dapat diunduh."
                    } else {
                        "Status: Belum login. Opsional, gunakan jika link Instagram meminta login atau kena batas rate-limit."
                    },
                    fontSize = 12.5.sp,
                    color = SlateMuted
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenIgLogin,
                        colors = ButtonDefaults.buttonColors(containerColor = SlateDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Rounded.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (uiState.hasInstagramCookies) "Perbarui Login IG" else "Login IG", fontSize = 12.5.sp)
                    }

                    if (uiState.hasInstagramCookies) {
                        OutlinedButton(onClick = onClearIgCookies, shape = RoundedCornerShape(8.dp)) {
                            Text("Hapus Sesi", fontSize = 12.5.sp)
                        }
                    }
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
                    text = "• Video HD & Normal: Movies/TikIG Saver\n" +
                        "• Foto / Slide: Pictures/TikIG Saver\n" +
                        "• Audio MP3: Music/TikIG Saver",
                    fontSize = 12.5.sp,
                    color = SlateMuted,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

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
                TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(actionLabel, color = tint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
