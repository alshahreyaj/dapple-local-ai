package com.localai.cutout.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PhotoRestorationScreen(
    state: AppState,
    onBack: () -> Unit,
    onChoosePhoto: () -> Unit,
    onRestore: () -> Unit,
    onCancel: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onSaved: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modelsReady = state.restorationScratchInstalled && state.restorationLamaInstalled &&
        state.restorationColorizerInstalled && state.restorationEnhancerInstalled
    val source = state.restorationSource
    val result = state.restorationResult

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.clip(CircleShape).clickable(onClick = onBack).padding(9.dp))
                Column {
                    Text("DAPPLE TOOL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.1.sp)
                    Text("Photo Restoration", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Bring old photos back to life.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Repair scratches, restore color, and enhance detail. Your photo stays on your phone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (state.restorationPhotoDownscaled) item {
            Text("This large photo was resized to fit your phone's memory. The original stays unchanged; the saved result uses the edited size.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (source == null) {
            item {
                Card(shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Image(painterResource(com.localai.cutout.R.drawable.photo_restoration_1), "Photo Restoration preview", Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(17.dp)), contentScale = ContentScale.Fit)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Start with an old photo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Choose a JPG, PNG or WebP photo to restore.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = onChoosePhoto, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Choose a photo", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        } else if (result != null) {
            item {
                ImageResultCard("Restored photo", source, result, onError, onSaved, "photo-restored")
            }
            item {
                OutlinedButton(onClick = onChoosePhoto, enabled = !state.restorationProcessing, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text("Choose another photo")
                }
            }
        } else {
            item {
                Card(shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Image(source.asImageBitmap(), "Selected photo", Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(17.dp)), contentScale = ContentScale.Fit)
                        OutlinedButton(onClick = onChoosePhoto, enabled = !state.restorationProcessing && !state.restorationDownloading, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.Image, contentDescription = null)
                            Text("Choose another photo", Modifier.padding(start = 8.dp))
                        }
                        Button(
                            onClick = if (modelsReady) onRestore else onDownload,
                            enabled = !state.restorationProcessing && !state.restorationDownloading,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(if (modelsReady) "Restore photo" else "Download tool · about 488 MB", fontWeight = FontWeight.SemiBold)
                        }
                        if (!modelsReady) {
                            Text("Colorization and detail enhancement are included in every restoration.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (state.restorationDownloading) item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Getting Photo Restoration ready", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("${state.restorationProgress}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    LinearProgressIndicator(progress = { state.restorationProgress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Includes scratch repair, colorization and detail enhancement.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onCancelDownload, modifier = Modifier.align(Alignment.End)) { Text("Cancel download") }
                }
            }
        }

        if (state.restorationProcessing) item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    CircularProgressIndicator(Modifier.size(23.dp), strokeWidth = 2.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(state.restorationStage ?: "Restoring your photo…", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        if (state.restorationTileTotal > 0 && state.restorationStage?.startsWith("Repairing damage") == true) Text("Repair tile ${state.restorationTileProgress} of ${state.restorationTileTotal}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.restorationTileTotal > 0 && state.restorationStage?.startsWith("Enhancing details") == true) Text("Detail tile ${state.restorationTileProgress} of ${state.restorationTileTotal}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
        }

        state.restorationError?.let { message -> item {
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        } }
        if (state.status?.startsWith("Restored photo saved") == true) item {
            Text(state.status!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        if (!state.restorationProcessing && !state.restorationDownloading) {
            item { CompatibilityNote() }
            item { PrivacyNote() }
        }
        item { Spacer(Modifier.height(6.dp)) }
    }
}

