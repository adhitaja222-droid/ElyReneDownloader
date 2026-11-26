package com.tikigsaver.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.tikigsaver.app.model.MediaContent
import com.tikigsaver.app.model.PhotoSlideItem
import com.tikigsaver.app.model.VideoQualityOption
import com.tikigsaver.app.ui.theme.CardBorder
import com.tikigsaver.app.ui.theme.EmeraldBright
import com.tikigsaver.app.ui.theme.EmeraldPrimary
import com.tikigsaver.app.ui.theme.SlateDark
import com.tikigsaver.app.ui.theme.SlateMuted

@Composable
fun MediaResultSection(
    media: MediaContent,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadStatus: String,
    downloadingPhotoIndex: Int?,
    downloadedPhotoIndices: Set<Int>,
    onDownloadVideoHd: (VideoQualityOption) -> Unit,
    onDownloadVideoNormal: (VideoQualityOption) -> Unit,
    onDownloadMp3: () -> Unit,
    onDownloadPhoto: (PhotoSlideItem) -> Unit,
    onDownloadAllPhotos: () -> Unit
) {
    var previewPhoto by remember { mutableStateOf<PhotoSlideItem?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Summary Card (thumbnail + caption/hashtags + Video HD / Normal / MP3 buttons)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, CardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .width(92.dp)
                            .height(124.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!media.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = media.thumbnailUrl,
                                contentDescription = media.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (media.hasPhotos && !media.hasVideo) Icons.Rounded.Image else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SlateDark
                            ) {
                                Text(
                                    text = media.platform.label,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (media.author.isNotBlank()) {
                                Text(
                                    text = "@${media.author}",
                                    color = SlateMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Text(
                            text = media.title,
                            color = SlateDark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 19.sp
                        )

                        media.hdVideo?.let { hd ->
                            Text(
                                text = "Asli terdeteksi: ${hd.resolutionSummary}",
                                color = EmeraldPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Action Buttons: Download Video HD, Download Video, MP3
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    media.hdVideo?.let { hdOption ->
                        Button(
                            onClick = { onDownloadVideoHd(hdOption) },
                            enabled = !isDownloading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Rounded.Hd, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = hdOption.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                                Text(
                                    text = hdOption.resolutionSummary,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }

                    media.normalVideo?.let { normalOption ->
                        Button(
                            onClick = { onDownloadVideoNormal(normalOption) },
                            enabled = !isDownloading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp)
                        ) {
                            Icon(Icons.Rounded.HighQuality, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = normalOption.label,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                )
                                Text(
                                    text = normalOption.resolutionSummary,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }

                    if (media.hasMp3) {
                        Button(
                            onClick = onDownloadMp3,
                            enabled = !isDownloading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldBright),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Download MP3 (Audio Saja)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }

                if (isDownloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = EmeraldPrimary
                        )
                        Text(
                            text = downloadStatus.ifBlank { "Memproses..." },
                            color = SlateMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Photo / Slide Picker Grid (matching the user's screenshot!)
        if (media.hasPhotos) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pilih Foto (${media.photos.size} Slide)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SlateDark
                )

                if (media.photos.size > 1) {
                    Button(
                        onClick = onDownloadAllPhotos,
                        enabled = !isDownloading && downloadingPhotoIndex == null,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Download Semua", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }

            val rows = media.photos.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { photo ->
                            PhotoSlideCard(
                                photo = photo,
                                isDownloadingThis = downloadingPhotoIndex == photo.index,
                                isDownloaded = downloadedPhotoIndices.contains(photo.index),
                                isBusy = isDownloading || downloadingPhotoIndex != null,
                                onPreviewClick = { previewPhoto = photo },
                                onDownloadClick = { onDownloadPhoto(photo) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    previewPhoto?.let { item ->
        Dialog(
            onDismissRequest = { previewPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { previewPhoto = null }
            ) {
                AsyncImage(
                    model = item.url,
                    contentDescription = "Slide ${item.index}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
                IconButton(
                    onClick = { previewPhoto = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Tutup", tint = Color.White)
                }
                Button(
                    onClick = {
                        onDownloadPhoto(item)
                        previewPhoto = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldBright),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download Image #${item.index}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PhotoSlideCard(
    photo: PhotoSlideItem,
    isDownloadingThis: Boolean,
    isDownloaded: Boolean,
    isBusy: Boolean,
    onPreviewClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.85f)
                    .background(Color(0xFFE2E8F0))
                    .clickable(onClick = onPreviewClick)
            ) {
                AsyncImage(
                    model = photo.url,
                    contentDescription = "Foto ${photo.index}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (photo.isVideo) {
                            Icon(
                                Icons.Rounded.VideoFile,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = if (photo.isVideo) "#${photo.index} Video" else "#${photo.index}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Emerald green Download Image button right under each photo (like screenshot)
            Button(
                onClick = onDownloadClick,
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDownloaded) Color(0xFF15803D) else EmeraldBright
                ),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp)
            ) {
                when {
                    isDownloadingThis -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Mengunduh...", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    isDownloaded -> {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tersimpan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    else -> {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (photo.isVideo) "Download Video" else "Download Image",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
