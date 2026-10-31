package com.localai.cutout.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.Image as ComposeImage
import com.localai.cutout.catalog.AiFeature
import com.localai.cutout.catalog.FeatureCatalog
import com.localai.cutout.catalog.ImplementationStatus
import com.localai.cutout.catalog.PreviewStyle
import com.localai.cutout.catalog.ToolCategory
import com.localai.cutout.core.image.ImageExporter
import com.localai.cutout.R

@Composable
fun ExploreScreen(
    state: AppState,
    listState: LazyListState,
    onFeature: (AiFeature) -> Unit,
    onDownload: () -> Unit,
    onOpen: (AiFeature) -> Unit,
    onCancel: () -> Unit,
    onUpscalerDownload: () -> Unit = onDownload,
    onCancelUpscaler: () -> Unit = onCancel,
    onSpeechDownload: () -> Unit = onDownload,
    onCancelSpeech: () -> Unit = onCancel,
    onObjectDownload: () -> Unit = onDownload,
    onCancelObject: () -> Unit = onCancel,
    onTranscriptionDownload: () -> Unit = onDownload,
    onCancelTranscription: () -> Unit = onCancel,
    modifier: Modifier = Modifier,
) {
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    val categories = listOf("All") + ToolCategory.entries.map { it.label }
    var layout by rememberSaveable { mutableStateOf("grid") }
    val filtered = FeatureCatalog.all.filter { feature ->
        (category == "All" || feature.category.label == category) &&
            (search.isBlank() || feature.title.contains(search, true) || feature.description.contains(search, true))
    }.sortedWith(
        compareBy<AiFeature> { if (it.status == ImplementationStatus.READY) 0 else 1 }
            .thenBy { it.deviceCompatibilityOrder },
    )
    val availableCount = filtered.count { it.status == ImplementationStatus.READY }
    val comingSoonCount = filtered.size - availableCount
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { LocoraWordmark() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Create more. Keep it yours.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
                Text("Explore AI tools for photos and audio. Choose a tool to download it; your files are processed on your phone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { SearchBox(search, { search = it }, category, categories, { category = it }) }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { item ->
                    FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                SectionTitle(if (search.isBlank()) "YOUR AI TOOLKIT" else "SEARCH RESULTS", if (search.isBlank()) "$availableCount available · $comingSoonCount coming soon" else "${filtered.size} matches")
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    FilterChip(selected = layout == "grid", onClick = { layout = "grid" }, label = { Text("Grid") })
                    FilterChip(selected = layout == "list", onClick = { layout = "list" }, label = { Text("List") })
                }
            }
        }
        if (filtered.isEmpty()) item { EmptyState("No tools found", "Try another search or category.") }
        else if (layout == "grid") items(filtered.chunked(2).size) { row ->
            val pair = filtered.chunked(2)[row]
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { feature ->
                    val installed = when (feature.id) { FeatureCatalog.backgroundRemover.id -> state.installed; "image-upscaler" -> state.upscalerInstalled; "noise-remover" -> state.speechInstalled; "speech-to-text" -> state.transcriptionInstalled; "object-remover" -> state.objectInstalled; else -> false }
                    val downloading = when (feature.id) { FeatureCatalog.backgroundRemover.id -> state.downloading; "image-upscaler" -> state.upscalerDownloading; "noise-remover" -> state.speechDownloading; "speech-to-text" -> state.transcriptionDownloading; "object-remover" -> state.objectDownloading; else -> false }
                    val progress = when (feature.id) { "image-upscaler" -> state.upscalerProgress; "noise-remover" -> state.speechProgress; "speech-to-text" -> state.transcriptionProgress; "object-remover" -> state.objectProgress; else -> state.downloadProgress }
                    FeatureCard(
                        feature = feature,
                        installed = installed,
                        downloading = downloading,
                        progress = progress,
                        onClick = { if (feature.status == ImplementationStatus.READY && installed) onOpen(feature) else onFeature(feature) },
                        onAction = { if (feature.status == ImplementationStatus.READY) { if (installed) onOpen(feature) else when (feature.id) { "image-upscaler" -> onUpscalerDownload(); "noise-remover" -> onSpeechDownload(); "speech-to-text" -> onTranscriptionDownload(); "object-remover" -> onObjectDownload(); else -> onDownload() } } },
                        onCancel = when (feature.id) { "image-upscaler" -> onCancelUpscaler; "noise-remover" -> onCancelSpeech; "speech-to-text" -> onCancelTranscription; "object-remover" -> onCancelObject; else -> onCancel },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        else items(filtered.size) { index ->
            val feature = filtered[index]
            val installed = when (feature.id) { FeatureCatalog.backgroundRemover.id -> state.installed; "image-upscaler" -> state.upscalerInstalled; "noise-remover" -> state.speechInstalled; "speech-to-text" -> state.transcriptionInstalled; "object-remover" -> state.objectInstalled; else -> false }
            val downloading = when (feature.id) { FeatureCatalog.backgroundRemover.id -> state.downloading; "image-upscaler" -> state.upscalerDownloading; "noise-remover" -> state.speechDownloading; "speech-to-text" -> state.transcriptionDownloading; "object-remover" -> state.objectDownloading; else -> false }
            FeatureListItem(
                feature = feature,
                installed = installed,
                downloading = downloading,
                progress = when (feature.id) { "image-upscaler" -> state.upscalerProgress; "noise-remover" -> state.speechProgress; "speech-to-text" -> state.transcriptionProgress; "object-remover" -> state.objectProgress; else -> state.downloadProgress },
                onClick = { if (feature.status == ImplementationStatus.READY && installed) onOpen(feature) else onFeature(feature) },
                onAction = { if (feature.status == ImplementationStatus.READY) { if (installed) onOpen(feature) else when (feature.id) { "image-upscaler" -> onUpscalerDownload(); "noise-remover" -> onSpeechDownload(); "speech-to-text" -> onTranscriptionDownload(); "object-remover" -> onObjectDownload(); else -> onDownload() } } },
                onCancel = when (feature.id) { "image-upscaler" -> onCancelUpscaler; "noise-remover" -> onCancelSpeech; "speech-to-text" -> onCancelTranscription; "object-remover" -> onCancelObject; else -> onCancel },
            )
        }
        item { PrivacyNote() }
    }
}

@Composable
private fun LocoraWordmark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ComposeImage(
            painter = painterResource(R.drawable.logo),
            contentDescription = "Dapple logo",
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(verticalArrangement = Arrangement.spacedBy((-3).dp)) {
            Text("Dapple", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            Text("Private tools. Your files stay on your phone.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SearchBox(
    value: String,
    onValue: (String) -> Unit,
    category: String,
    categories: List<String>,
    onCategorySelected: (String) -> Unit,
) {
    var filterMenuExpanded by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(value, onValue, modifier = Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface), decorationBox = { inner ->
                if (value.isEmpty()) Text("Search AI tools", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                inner()
            })
            Box {
                IconButton(onClick = { filterMenuExpanded = true }) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Filter by category", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = filterMenuExpanded, onDismissRequest = { filterMenuExpanded = false }) {
                    categories.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(if (category == option) "$option ✓" else option) },
                            onClick = {
                                onCategorySelected(option)
                                filterMenuExpanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(eyebrow: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FeatureCard(feature: AiFeature, installed: Boolean, downloading: Boolean, progress: Int, onClick: () -> Unit, onAction: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.animateContentSize().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FeaturePreview(feature, Modifier.fillMaxWidth().height(122.dp).clip(RoundedCornerShape(15.dp)))
            Column(Modifier.padding(horizontal = 3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(feature.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(feature.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                CategoryAndSize(feature)
            }
            FeatureAction(installed, downloading, progress, feature, onAction, onCancel, compact = false)
        }
    }
}

@Composable
private fun FeatureListItem(
    feature: AiFeature,
    installed: Boolean,
    downloading: Boolean,
    progress: Int,
    onClick: () -> Unit,
    onAction: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
            FeaturePreview(feature, Modifier.size(92.dp).clip(RoundedCornerShape(15.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(feature.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(feature.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                CategoryAndSize(feature)
                FeatureAction(installed, downloading, progress, feature, onAction, onCancel, compact = true)
            }
        }
    }
}

@Composable
private fun CategoryAndSize(feature: AiFeature) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val icon = when (feature.category) {
            ToolCategory.IMAGE -> Icons.Rounded.Image
            ToolCategory.AUDIO -> Icons.Rounded.GraphicEq
            ToolCategory.TEXT -> Icons.Rounded.Description
            ToolCategory.VIDEO -> Icons.Rounded.Movie
        }
        Icon(icon, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
        Text(feature.category.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        feature.modelSize?.let { Text("·  $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun FeatureAction(installed: Boolean, downloading: Boolean, progress: Int, feature: AiFeature, onAction: () -> Unit, onCancel: () -> Unit, compact: Boolean) {
    when {
        feature.status == ImplementationStatus.COMING_SOON -> Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text("Coming soon", Modifier.fillMaxWidth().padding(vertical = 10.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        downloading -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (progress >= 0) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (progress >= 0) "$progress%" else "Downloading", style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Cancel") }
            }
        }
        else -> Button(onClick = onAction, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = if (compact) 11.dp else 9.dp)) {
            Text(when { feature.updateAvailable -> "Update"; installed -> "Open"; else -> "Download" }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun FeatureArtwork(style: PreviewStyle, modifier: Modifier = Modifier) {
    val surface = when (style) {
        PreviewStyle.CUTOUT -> Color(0xFFD9EAE0)
        PreviewStyle.UPSCALE -> Color(0xFFE5E1F4)
        PreviewStyle.ERASE -> Color(0xFFF1E2D7)
        PreviewStyle.ENHANCE -> Color(0xFFDCE9F2)
        PreviewStyle.COLOR -> Color(0xFFF4E4C9)
        PreviewStyle.SOUND, PreviewStyle.TRANSCRIBE -> Color(0xFFDCE7F0)
        PreviewStyle.VIDEO -> Color(0xFFE5E8DA)
        PreviewStyle.DOCUMENT -> Color(0xFFE8E6DC)
        PreviewStyle.RESTORE -> Color(0xFFF0E2D1)
        PreviewStyle.TRANSLATE -> Color(0xFFDCE9E7)
        PreviewStyle.VIDEO_CUTOUT -> Color(0xFFE5E8DA)
        PreviewStyle.VIDEO_ENHANCE -> Color(0xFFE3E2F1)
    }
    Canvas(modifier.background(surface)) {
        val w = size.width; val h = size.height
        if (style == PreviewStyle.SOUND || style == PreviewStyle.TRANSCRIBE) {
            val colors = listOf(Color(0xFF4F7865), Color(0xFF78A58A), Color(0xFF8C9AB3))
            val count = 36
            val gap = w / (count * 1.7f)
            for (i in 0 until count) {
                val centerX = (i + .5f) * gap * 1.7f
                val wave = (kotlin.math.sin(i * .73) * .5 + .5).toFloat()
                val barH = h * (.18f + wave * .5f)
                drawRoundRect(colors[i % colors.size], Offset(centerX, (h - barH) / 2), Size(gap, barH), androidx.compose.ui.geometry.CornerRadius(gap))
            }
            if (style == PreviewStyle.TRANSCRIBE) drawRoundRect(Color.White.copy(alpha = .82f), Offset(w * .2f, h * .74f), Size(w * .6f, h * .12f), androidx.compose.ui.geometry.CornerRadius(12f))
        } else if (style == PreviewStyle.DOCUMENT) {
            val pageW = w * .48f; val pageH = h * .78f
            val left = (w - pageW) / 2; val top = (h - pageH) / 2
            drawRoundRect(Color.White, Offset(left, top), Size(pageW, pageH), androidx.compose.ui.geometry.CornerRadius(12f))
            for (line in 0..3) drawRoundRect(Color(0xFF91A99A), Offset(left + pageW * .2f, top + pageH * (.34f + line * .12f)), Size(pageW * .6f, h * .018f), androidx.compose.ui.geometry.CornerRadius(5f))
            val scan = Color(0xFF39735C)
            val arm = pageW * .2f; val stroke = h * .025f
            listOf(Offset(left - arm * .16f, top), Offset(left + pageW, top), Offset(left - arm * .16f, top + pageH), Offset(left + pageW, top + pageH)).forEachIndexed { index, point ->
                val xDirection = if (index == 0 || index == 2) 1 else -1
                val yDirection = if (index < 2) 1 else -1
                drawRoundRect(scan, Offset(point.x + if (xDirection < 0) -arm else 0f, point.y), Size(arm, stroke), androidx.compose.ui.geometry.CornerRadius(stroke))
                drawRoundRect(scan, Offset(point.x, point.y + if (yDirection < 0) -arm else 0f), Size(stroke, arm), androidx.compose.ui.geometry.CornerRadius(stroke))
            }
            drawRoundRect(Color(0xFFB7DCC8), Offset(w * .66f, h * .66f), Size(w * .2f, h * .12f), androidx.compose.ui.geometry.CornerRadius(8f))
        } else if (style == PreviewStyle.TRANSLATE) {
            drawRoundRect(Color.White, Offset(w * .12f, h * .2f), Size(w * .7f, h * .27f), androidx.compose.ui.geometry.CornerRadius(15f))
            drawRoundRect(Color.White.copy(alpha = .78f), Offset(w * .2f, h * .55f), Size(w * .7f, h * .27f), androidx.compose.ui.geometry.CornerRadius(15f))
            repeat(2) { line ->
                drawRoundRect(Color(0xFF659783), Offset(w * .2f, h * (.29f + line * .09f)), Size(w * (.38f - line * .07f), h * .025f), androidx.compose.ui.geometry.CornerRadius(6f))
                drawRoundRect(Color(0xFF8294B1), Offset(w * .3f, h * (.64f + line * .09f)), Size(w * (.42f - line * .07f), h * .025f), androidx.compose.ui.geometry.CornerRadius(6f))
            }
            drawCircle(Color(0xFF4F7865), w * .075f, Offset(w * .78f, h * .49f))
            drawCircle(Color.White, w * .018f, Offset(w * .78f, h * .49f))
        } else {
            val photoW = w * .62f; val photoH = h * .82f; val left = (w - photoW) / 2; val top = (h - photoH) / 2
            drawRoundRect(Color.White.copy(alpha = .9f), Offset(left, top), Size(photoW, photoH), androidx.compose.ui.geometry.CornerRadius(19f))
            drawRoundRect(Color(0xFFB8D4CB), Offset(left + 8, top + 8), Size(photoW - 16, photoH - 16), androidx.compose.ui.geometry.CornerRadius(14f))
            drawCircle(Color(0xFFF3D08D), photoW * .12f, Offset(left + photoW * .72f, top + photoH * .28f))
            val hill = Path().apply { moveTo(left + 8, top + photoH * .72f); lineTo(left + photoW * .37f, top + photoH * .44f); lineTo(left + photoW * .59f, top + photoH * .66f); lineTo(left + photoW * .77f, top + photoH * .5f); lineTo(left + photoW - 8, top + photoH * .78f); lineTo(left + photoW - 8, top + photoH - 8); lineTo(left + 8, top + photoH - 8); close() }
            drawPath(hill, Color(0xFF6D9984))
            if (style == PreviewStyle.CUTOUT || style == PreviewStyle.ERASE || style == PreviewStyle.VIDEO_CUTOUT) {
                drawCircle(Color(0xFF3F594B), photoW * .08f, Offset(left + photoW * .48f, top + photoH * .49f))
                drawRoundRect(Color(0xFF3F594B), Offset(left + photoW * .39f, top + photoH * .58f), Size(photoW * .18f, photoH * .22f), androidx.compose.ui.geometry.CornerRadius(photoW * .1f))
            }
            if (style == PreviewStyle.UPSCALE || style == PreviewStyle.ENHANCE || style == PreviewStyle.COLOR || style == PreviewStyle.VIDEO_ENHANCE) {
                drawCircle(Color.White.copy(alpha = .88f), w * .065f, Offset(left + photoW * .9f, top + photoH * .18f))
                drawCircle(Color(0xFFDBA957), w * .025f, Offset(left + photoW * .9f, top + photoH * .18f))
            }
            if (style == PreviewStyle.RESTORE) {
                drawLine(Color.White.copy(alpha = .95f), Offset(left + photoW * .5f, top + 8), Offset(left + photoW * .5f, top + photoH - 8), strokeWidth = w * .012f)
                drawCircle(Color(0xFFE8C986), w * .035f, Offset(left + photoW * .22f, top + photoH * .22f))
                drawCircle(Color.White, w * .025f, Offset(left + photoW * .78f, top + photoH * .76f))
            }
            if (style == PreviewStyle.VIDEO_CUTOUT || style == PreviewStyle.VIDEO_ENHANCE) {
                val center = Offset(w * .5f, h * .5f)
                drawCircle(Color.White.copy(alpha = .9f), w * .1f, center)
                val play = Path().apply { moveTo(center.x - w * .025f, center.y - h * .04f); lineTo(center.x + w * .04f, center.y); lineTo(center.x - w * .025f, center.y + h * .04f); close() }
                drawPath(play, Color(0xFF416956))
            }
        }
    }
}

@Composable
private fun FeaturePreview(feature: AiFeature, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val resource = feature.previewResource
    if (resource != null) {
        ComposeImage(painterResource(resource), contentDescription = feature.title, modifier = modifier, contentScale = contentScale)
    } else {
        FeatureArtwork(feature.preview, modifier)
    }
}

@Composable
fun DetailsScreen(feature: AiFeature, state: AppState, onBack: () -> Unit, onDownload: () -> Unit, onOpen: () -> Unit, onCancel: () -> Unit, onSaved: () -> Unit, onError: (String) -> Unit, modifier: Modifier = Modifier, onUpscalerDownload: () -> Unit = onDownload, onUpscalerCancel: () -> Unit = onCancel, onSpeechDownload: () -> Unit = onDownload, onCancelSpeech: () -> Unit = onCancel, onObjectDownload: () -> Unit = onDownload, onCancelObject: () -> Unit = onCancel, onTranscriptionDownload: () -> Unit = onDownload, onCancelTranscription: () -> Unit = onCancel) {
    val ready = feature.status == ImplementationStatus.READY && when (feature.id) { "image-upscaler" -> state.upscalerInstalled; "noise-remover" -> state.speechInstalled; "speech-to-text" -> state.transcriptionInstalled; "object-remover" -> state.objectInstalled; else -> state.installed }
    val downloading = when (feature.id) { "image-upscaler" -> state.upscalerDownloading; "noise-remover" -> state.speechDownloading; "speech-to-text" -> state.transcriptionDownloading; "object-remover" -> state.objectDownloading; else -> feature.id == FeatureCatalog.backgroundRemover.id && state.downloading }
    val progress = when (feature.id) { "image-upscaler" -> state.upscalerProgress; "noise-remover" -> state.speechProgress; "speech-to-text" -> state.transcriptionProgress; "object-remover" -> state.objectProgress; else -> state.downloadProgress }
    val downloadAction = when (feature.id) { "image-upscaler" -> onUpscalerDownload; "noise-remover" -> onSpeechDownload; "speech-to-text" -> onTranscriptionDownload; "object-remover" -> onObjectDownload; else -> onDownload }
    val cancelAction = when (feature.id) { "image-upscaler" -> onUpscalerCancel; "noise-remover" -> onCancelSpeech; "speech-to-text" -> onCancelTranscription; "object-remover" -> onCancelObject; else -> onCancel }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.clip(CircleShape).clickable(onClick = onBack).padding(9.dp))
                Text("About this tool", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        item { FeaturePreview(feature, Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(24.dp)), ContentScale.Fit) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SmallTag(feature.category.label)
                    SmallTag(if (feature.status == ImplementationStatus.READY) "Ready on your phone" else "Coming soon")
                }
                Text(feature.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(feature.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            when {
                feature.status == ImplementationStatus.COMING_SOON -> Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("This tool isn't available yet. We're working on bringing it to Dapple.", Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
                downloading -> Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(if (progress >= 0) "Getting your tool ready · $progress%" else "Getting your tool ready…", style = MaterialTheme.typography.labelLarge)
                    if (progress >= 0) LinearProgressIndicator(progress = { progress / 100f }, Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedButton(onClick = cancelAction, modifier = Modifier.fillMaxWidth()) { Text("Cancel download") }
                }
                else -> Button(onClick = if (ready && !feature.updateAvailable) onOpen else downloadAction, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp)) {
                    Text(when { feature.updateAvailable -> "Update tool"; ready -> "Open tool"; else -> "Download tool · ${feature.modelSize}" }, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (feature.status == ImplementationStatus.READY) {
            val featureStatus = when (feature.id) { "noise-remover" -> state.speechError; "speech-to-text" -> state.transcriptionError; "object-remover" -> state.objectError; else -> state.status }
            if (featureStatus != null && !downloading) item {
                Text(featureStatus, color = if (featureStatus.startsWith("Saved")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            item { SectionTitle("TOOL INFORMATION", "Before you download") }
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        InfoRow("Space needed", feature.modelSize ?: "Not specified")
                        InfoRow("Version", feature.modelVersion ?: "Not specified")
                        InfoRow("Works on", feature.runtime ?: "Your phone")
                        InfoRow("Usage terms", feature.license ?: "Not specified")
                        InfoRow("Works with", feature.supportedFormats.joinToString().ifBlank { "Not specified" })
                        InfoRow("Phone compatibility", feature.requirements ?: "No special setup", last = true)
                    }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Your files stay yours", fontWeight = FontWeight.SemiBold)
                            Text(when (feature.category) {
                                ToolCategory.AUDIO -> "Your recordings stay on your phone while this tool works. They aren't uploaded."
                                ToolCategory.TEXT -> "Your text stays on your phone while this tool works. It isn't uploaded."
                                ToolCategory.VIDEO -> "Your videos stay on your phone while this tool works. They aren't uploaded."
                                ToolCategory.IMAGE -> "Your photos stay on your phone while this tool works. They aren't uploaded."
                            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { CompatibilityNote() }
            if (feature.id == FeatureCatalog.backgroundRemover.id && state.result != null) item {
                CutoutResultCard(state.backgroundSource, state.result, onError = onError, onSaved = onSaved)
            }
            if (feature.id == "image-upscaler" && state.upscaleResult != null) item {
                ImageResultCard("Photo enhanced · 4×", state.upscalerSource, state.upscaleResult, onError, onSaved, "photo-enhanced")
            }
            if (feature.id == "object-remover" && state.objectResult != null) item {
                ImageResultCard("Object removed", state.objectSource, state.objectResult, onError, onSaved, "object-removed")
            }
        }
    }
}

@Composable
fun ToolScreen(
    state: AppState,
    onBack: () -> Unit,
    onChoosePhoto: () -> Unit,
    onRemoveBackground: () -> Unit,
    onCancel: () -> Unit,
    onCancelDownload: () -> Unit,
    onSaved: () -> Unit,
    onError: (String) -> Unit,
    onPreparePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photo = state.backgroundSource ?: state.source
    LaunchedEffect(photo, state.backgroundSource, state.busy) {
        if (state.backgroundSource == null && photo != null && state.status == null) onPreparePhoto()
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.clip(CircleShape).clickable(onClick = onBack).padding(9.dp))
                Column {
                    Text("DAPPLE PHOTO TOOL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.1.sp)
                    Text("Background Remover", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Keep the subject. Clear the background.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Choose a photo, preview the result, then save it with a clear background. Your photo stays on your phone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.backgroundPhotoDownscaled) item { ResolutionNote() }
        if (state.result != null) item {
            CutoutResultCard(photo, state.result, onError = onError, onSaved = onSaved)
        } else item {
            Card(shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (photo != null) {
                        ComposeImage(photo.asImageBitmap(), "Selected photo", Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(17.dp)), contentScale = ContentScale.Fit)
                    } else {
                        FeaturePreview(FeatureCatalog.backgroundRemover, Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(17.dp)), ContentScale.Fit)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Start with a photo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("JPG, PNG or WebP · Save as PNG with a clear background", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (photo == null) {
                        Button(onClick = onChoosePhoto, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                            Text("Choose a photo", fontWeight = FontWeight.SemiBold)
                        }
                    } else if (state.result == null) {
                        Button(onClick = onRemoveBackground, enabled = !state.busy && !state.downloading, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                            Text(if (state.installed) "Remove background" else "Download tool & remove background", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(onClick = onChoosePhoto, enabled = !state.busy && !state.downloading, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Text("Choose another photo")
                        }
                    }
                }
            }
        }
        if (state.result != null) item {
            OutlinedButton(onClick = onChoosePhoto, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("Choose another photo")
            }
        }
        if (state.busy) item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    CircularProgressIndicator(Modifier.size(23.dp), strokeWidth = 2.dp)
                    Text(state.status ?: "Working on your photo…", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
        }
        if (state.downloading) item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Getting Background Remover ready · 176 MB", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("${state.downloadProgress}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    LinearProgressIndicator(progress = { state.downloadProgress / 100f }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = onCancelDownload, modifier = Modifier.align(Alignment.End)) { Text("Cancel download") }
                }
            }
        }
        if (state.status != null && !state.busy && !state.downloading) item {
            Text(state.status, style = MaterialTheme.typography.bodySmall, color = if (state.status.startsWith("Saved")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
        item { CompatibilityNote() }
        item { PrivacyNote() }
    }
}

@Composable
fun SpeechNoiseRemoverScreen(
    state: AppState,
    onBack: () -> Unit,
    onChooseAudio: () -> Unit,
    onRemoveNoise: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onSaveWav: () -> Unit,
    onCancelProcessing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.clip(CircleShape).clickable(onClick = onBack).padding(9.dp))
                Column {
                    Text("DAPPLE AUDIO TOOL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.1.sp)
                    Text("Speech Noise Remover", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Make speech easier to hear.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Reduce background noise in a voice recording. Choose a WAV, MP3, M4A, FLAC or OGG file. Some file types may not work on every phone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.GraphicEq, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(state.audioName ?: "No recording selected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(if (state.audioSource == null) "Select an audio file" else "Original audio", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (state.audioSource != null) OutlinedButton(onClick = { onPlayAudio("original") }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.audioPlaying == "original") "Stop playback" else "Play original")
                    }
                    if (state.audioSource != null) TextButton(onClick = onChooseAudio, modifier = Modifier.fillMaxWidth()) { Text("Choose a different file") }
                    Button(onClick = if (state.audioSource == null) onChooseAudio else onRemoveNoise, enabled = !state.speechBusy && !state.speechDownloading, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                        Text(if (state.audioSource == null) "Choose audio" else if (state.speechBusy) "Removing noise…" else "Remove background noise")
                    }
                    if (state.speechBusy) {
                        LinearProgressIndicator(progress = { if (state.speechTileTotal == 0) 0f else state.speechTileProgress.toFloat() / state.speechTileTotal }, Modifier.fillMaxWidth())
                        if (state.speechTileTotal > 0) Text("Cleaning up audio · ${state.speechTileProgress * 100 / state.speechTileTotal}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = onCancelProcessing, modifier = Modifier.fillMaxWidth()) { Text("Cancel processing") }
                    }
                }
            }
        }
        if (state.enhancedAudioPath != null) item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text("BEFORE & AFTER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.1.sp)
                    Text("Cleaned-up recording", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = { onPlayAudio("enhanced") }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.audioPlaying == "enhanced") "Stop playback" else "Play cleaned-up version")
                    }
                    Button(onClick = onSaveWav, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text("Save as WAV") }
                }
            }
        }
        if (state.speechError != null) item { Text(state.speechError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (state.status == "Enhanced WAV saved") item { Text(state.status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        item { CompatibilityNote() }
        item { PrivacyNote() }
    }
}

@Composable
fun UpscalerToolScreen(state: AppState, onBack: () -> Unit, onChoosePhoto: () -> Unit, onUpscale: () -> Unit, onCancel: () -> Unit, onSaved: () -> Unit, onError: (String) -> Unit, onPreparePhoto: () -> Unit, modifier: Modifier = Modifier) {
    val photo = state.upscalerSource ?: state.source
    LaunchedEffect(photo, state.upscalerSource, state.busy) {
        if (state.upscalerSource == null && photo != null && state.status == null && state.upscalerError == null) onPreparePhoto()
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.clip(CircleShape).clickable(onClick = onBack).padding(9.dp))
                Column { Text("DAPPLE TOOL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.1.sp); Text("Photo Enhancer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Bring more detail to your photos.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Make your photo four times larger and bring out more detail. Preview it before saving.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.upscalerPhotoDownscaled) item { ResolutionNote() }
        if (state.upscaleResult != null) {
            item { ImageResultCard("Photo enhanced · 4×", photo, state.upscaleResult, onError, onSaved, "photo-enhanced") }
            item {
                OutlinedButton(onClick = onChoosePhoto, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text("Choose another photo")
                }
            }
        }
        else item {
            Card(shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (photo != null) ComposeImage(photo.asImageBitmap(), "Selected photo", Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(17.dp)), contentScale = ContentScale.Fit)
                    else {
                        FeaturePreview(FeatureCatalog.all.first { it.id == "image-upscaler" }, Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(17.dp)), ContentScale.Fit)
                        Text("Select a photo to preview it before upscaling.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (photo == null) Button(onClick = onChoosePhoto, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Choose a photo") }
                    else {
                        Button(onClick = onUpscale, enabled = !state.busy && !state.upscalerDownloading, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text(if (!state.upscalerInstalled) "Download tool · 17.5 MB" else "Enhance photo") }
                        OutlinedButton(onClick = onChoosePhoto, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Choose another photo") }
                    }
                }
            }
        }
        if (state.upscalerDownloading) item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Getting Photo Enhancer ready · ${state.upscalerProgress}%", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { state.upscalerProgress / 100f }, Modifier.fillMaxWidth())
                    TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
                }
            }
        }
        if (state.busy) item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    CircularProgressIndicator(Modifier.size(23.dp), strokeWidth = 2.dp)
                    Column(Modifier.weight(1f)) {
                        Text(state.status ?: "Enhancing your photo…", style = MaterialTheme.typography.bodyMedium)
                        if (state.upscalerTileTotal > 0) Text("Enhancing photo · ${state.upscalerTileProgress * 100 / state.upscalerTileTotal}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
        }
        if (state.status != null && state.status != state.upscalerError && !state.busy && !state.upscalerDownloading) item { Text(state.status, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (state.upscalerError != null && !state.upscalerDownloading) item { Text(state.upscalerError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        item { CompatibilityNote() }
        item { PrivacyNote() }
    }
}

@Composable
private fun SmallTag(text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, Modifier.padding(horizontal = 11.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoRow(label: String, value: String, last: Boolean = false) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.widthIn(max = 190.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
    }
}

@Composable
fun DownloadsScreen(state: AppState, onOpen: () -> Unit, onDownload: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier, onOpenUpscaler: () -> Unit = onOpen, onDownloadUpscaler: () -> Unit = onDownload, onCancelUpscaler: () -> Unit = onCancel, onDeleteUpscaler: () -> Unit = onDelete, onOpenSpeech: () -> Unit = onOpen, onDownloadSpeech: () -> Unit = onDownload, onCancelSpeech: () -> Unit = onCancel, onDeleteSpeech: () -> Unit = onDelete, onOpenObject: () -> Unit = onOpen, onDownloadObject: () -> Unit = onDownload, onCancelObject: () -> Unit = onCancel, onDeleteObject: () -> Unit = onDelete, onOpenTranscription: () -> Unit = onOpen, onDownloadTranscription: () -> Unit = onDownload, onCancelTranscription: () -> Unit = onCancel, onDeleteTranscription: () -> Unit = onDelete) {
    var confirmDelete by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { LocoraWordmark() }
        item { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Your tools", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold); Text("See what's ready and free up space when you need to.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        if (state.downloading) item {
            SectionTitle("IN PROGRESS", "Downloading")
            ModelCard(state, onOpen, onDownload, onCancel) { confirmDelete = true }
        }
        if (state.installed) item {
            SectionTitle("INSTALLED", "Ready to use")
            ModelCard(state, onOpen, onDownload, onCancel) { confirmDelete = true }
        }
        if (state.upscalerDownloading || state.upscalerInstalled || state.upscalerError != null) item {
            SectionTitle(if (state.upscalerDownloading) "IN PROGRESS" else if (state.upscalerInstalled) "INSTALLED" else "AVAILABLE", if (state.upscalerDownloading) "Downloading" else if (state.upscalerInstalled) "Ready to use" else "Download didn't finish")
            UpscalerModelCard(state, onOpenUpscaler, onDownloadUpscaler, onCancelUpscaler, onDeleteUpscaler)
        }
        if (state.speechDownloading || state.speechInstalled || state.speechError != null) item {
            SectionTitle(if (state.speechDownloading) "IN PROGRESS" else if (state.speechInstalled) "INSTALLED" else "AVAILABLE", if (state.speechDownloading) "Downloading" else if (state.speechInstalled) "Ready to use" else "Download didn't finish")
            SpeechModelCard(state, onOpenSpeech, onDownloadSpeech, onCancelSpeech, onDeleteSpeech)
        }
        if (state.transcriptionDownloading || state.transcriptionInstalled || state.transcriptionError != null) item {
            SectionTitle(if (state.transcriptionDownloading) "IN PROGRESS" else if (state.transcriptionInstalled) "INSTALLED" else "AVAILABLE", if (state.transcriptionDownloading) "Downloading" else if (state.transcriptionInstalled) "Ready to use" else "Download didn't finish")
            TranscriptionModelCard(state, onOpenTranscription, onDownloadTranscription, onCancelTranscription, onDeleteTranscription)
        }
        if (state.objectDownloading || state.objectInstalled || state.objectError != null) item {
            SectionTitle(if (state.objectDownloading) "IN PROGRESS" else if (state.objectInstalled) "INSTALLED" else "AVAILABLE", if (state.objectDownloading) "Downloading" else if (state.objectInstalled) "Ready to use" else "Download didn't finish")
            ObjectModelCard(state, onOpenObject, onDownloadObject, onCancelObject, onDeleteObject)
        }
        if (!state.installed && !state.downloading && !state.upscalerInstalled && !state.upscalerDownloading && !state.speechInstalled && !state.speechDownloading && !state.transcriptionInstalled && !state.transcriptionDownloading && !state.objectInstalled && !state.objectDownloading && state.objectError == null && state.transcriptionError == null) item {
            EmptyState("Your tools are ready to add", "Choose a tool in Explore to download it. After setup, you can use it without internet.")
            OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text(if (state.status != null) "Try download again" else "Get Background Remover") }
        }
        if (state.status != null && !state.downloading) item { Text(state.status, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Remove this download?") },
        text = { Text("You can download Background Remover again from Explore whenever you need it.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } },
    )
}

@Composable
private fun SpeechModelCard(state: AppState, onOpen: () -> Unit, onDownload: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                FeaturePreview(FeatureCatalog.all.first { it.id == "noise-remover" }, Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Speech Noise Remover", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (state.speechInstalled) "Ready to use without internet" else "Noise reduction for recordings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (state.speechInstalled) formatSize(state.speechBytes) else if (state.speechDownloading) "${state.speechProgress}% downloaded" else "About 19.6 MB download", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (state.speechInstalled) Icon(Icons.Rounded.CheckCircle, "Ready to use", tint = MaterialTheme.colorScheme.primary)
            }
            if (state.speechDownloading) {
                LinearProgressIndicator(progress = { state.speechProgress / 100f }, Modifier.fillMaxWidth())
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel download") }
            } else if (state.speechInstalled) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onOpen, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Text("Open tool") }
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Rounded.DeleteOutline, null); Spacer(Modifier.width(6.dp)); Text("Delete") }
                }
            } else {
                if (state.speechError != null) Text(state.speechError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun TranscriptionModelCard(state: AppState, onOpen: () -> Unit, onDownload: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                FeaturePreview(FeatureCatalog.all.first { it.id == "speech-to-text" }, Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Speech-to-Text", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (state.transcriptionInstalled) "Ready to use without internet" else "English and German transcription", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (state.transcriptionInstalled) formatSize(state.transcriptionBytes) else if (state.transcriptionDownloading) "${state.transcriptionProgress}% downloaded" else "About 161 MB download", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (state.transcriptionInstalled) Icon(Icons.Rounded.CheckCircle, "Ready to use", tint = MaterialTheme.colorScheme.primary)
            }
            if (state.transcriptionDownloading) {
                LinearProgressIndicator(progress = { state.transcriptionProgress / 100f }, Modifier.fillMaxWidth())
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel download") }
            } else if (state.transcriptionInstalled) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onOpen, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Text("Open tool") }
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Rounded.DeleteOutline, null); Spacer(Modifier.width(6.dp)); Text("Delete") }
                }
            } else {
                if (state.transcriptionError != null) Text(state.transcriptionError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun ObjectModelCard(state: AppState, onOpen: () -> Unit, onDownload: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                FeaturePreview(FeatureCatalog.all.first { it.id == "object-remover" }, Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Object Remover", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (state.objectInstalled) "Ready to use without internet" else "Remove objects from photos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (state.objectInstalled) formatSize(state.objectBytes) else if (state.objectDownloading) { if (state.objectProgress >= 0) "${state.objectProgress}% downloaded" else "Downloading…" } else "174 MB download", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (state.objectInstalled) Icon(Icons.Rounded.CheckCircle, "Ready to use", tint = MaterialTheme.colorScheme.primary)
            }
            if (state.objectDownloading) {
                if (state.objectProgress >= 0) LinearProgressIndicator(progress = { state.objectProgress / 100f }, Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel download") }
            } else if (state.objectInstalled) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onOpen, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Text("Open tool") }
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Rounded.DeleteOutline, null); Spacer(Modifier.width(6.dp)); Text("Delete") }
                }
            } else {
                if (state.objectError != null) Text(state.objectError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun UpscalerModelCard(state: AppState, onOpen: () -> Unit, onDownload: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                FeaturePreview(FeatureCatalog.all.first { it.id == "image-upscaler" }, Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Photo Enhancer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (state.upscalerInstalled) "Ready to use without internet" else "Makes photos four times larger", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (state.upscalerInstalled) formatSize(state.upscalerBytes) else if (state.upscalerDownloading) "${state.upscalerProgress}% downloaded" else "About 17.5 MB download", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (state.upscalerInstalled) Icon(Icons.Rounded.CheckCircle, "Ready to use", tint = MaterialTheme.colorScheme.primary)
            }
            if (state.upscalerDownloading) {
                LinearProgressIndicator(progress = { state.upscalerProgress / 100f }, Modifier.fillMaxWidth())
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel download") }
            } else if (state.upscalerInstalled) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onOpen, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Text("Open tool") }
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Rounded.DeleteOutline, null); Spacer(Modifier.width(6.dp)); Text("Delete") }
                }
            } else {
                if (state.upscalerError != null) Text(state.upscalerError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text(if (state.upscalerError != null) "Try again" else "Download tool") }
            }
        }
    }
}

@Composable
private fun ModelCard(state: AppState, onOpen: () -> Unit, onDownload: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                FeaturePreview(FeatureCatalog.backgroundRemover, Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Background Remover", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (state.installed) "Ready to use without internet" else "Getting ready · About 176 MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (state.installed) formatSize(state.modelBytes) else "${state.downloadProgress}% downloaded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (state.installed) Icon(Icons.Rounded.CheckCircle, "Ready to use", tint = MaterialTheme.colorScheme.primary)
            }
            if (state.downloading) {
                LinearProgressIndicator(progress = { state.downloadProgress / 100f }, Modifier.fillMaxWidth())
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel download") }
            } else if (state.installed) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onOpen, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Text("Open tool") }
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Rounded.DeleteOutline, null); Spacer(Modifier.width(6.dp)); Text("Delete") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onDownload, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Text(if (state.status != null) "Try again" else "Download tool") }
                }
            }
        }
    }
}

@Composable
fun LocoraSettingsScreen(state: AppState, onTheme: (String) -> Unit, onDelete: () -> Unit, onPrivacyPolicy: () -> Unit, onLicenses: () -> Unit, modifier: Modifier = Modifier, onDeleteUpscaler: () -> Unit = onDelete, onDeleteSpeech: () -> Unit = onDelete, onDeleteObject: () -> Unit = onDelete, onDeleteTranscription: () -> Unit = onDelete) {
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDeleteUpscaler by remember { mutableStateOf(false) }
    var confirmDeleteSpeech by remember { mutableStateOf(false) }
    var confirmDeleteObject by remember { mutableStateOf(false) }
    var confirmDeleteTranscription by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { LocoraWordmark() }
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
        item {
            SectionTitle("APPEARANCE", "Theme preference")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                    FilterChip(selected = state.themePreference == value, onClick = { onTheme(value) }, label = { Text(label) })
                }
            }
        }
        item {
            SectionTitle("STORAGE", "Downloads & storage")
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val modelCount = (if (state.installed) 1 else 0) + (if (state.upscalerInstalled) 1 else 0) + (if (state.speechInstalled) 1 else 0) + (if (state.transcriptionInstalled) 1 else 0) + (if (state.objectInstalled) 1 else 0)
                    val usedBytes = state.modelBytes + state.upscalerBytes + state.speechBytes + state.transcriptionBytes + state.objectBytes
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Tools downloaded"); Text(modelCount.toString(), fontWeight = FontWeight.SemiBold) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Space used"); Text(formatSize(usedBytes), fontWeight = FontWeight.SemiBold) }
                    if (state.installed) TextButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, null); Spacer(Modifier.width(5.dp)); Text("Remove Background Remover") }
                    if (state.upscalerInstalled) TextButton(onClick = { confirmDeleteUpscaler = true }) { Icon(Icons.Rounded.DeleteOutline, null); Spacer(Modifier.width(5.dp)); Text("Remove Photo Enhancer") }
                    if (state.speechInstalled) TextButton(onClick = { confirmDeleteSpeech = true }) { Icon(Icons.Rounded.DeleteOutline, null); Spacer(Modifier.width(5.dp)); Text("Remove Speech Noise Remover") }
                    if (state.transcriptionInstalled) TextButton(onClick = { confirmDeleteTranscription = true }) { Icon(Icons.Rounded.DeleteOutline, null); Spacer(Modifier.width(5.dp)); Text("Remove Speech-to-Text model") }
                    if (state.objectInstalled) TextButton(onClick = { confirmDeleteObject = true }) { Icon(Icons.Rounded.DeleteOutline, null); Spacer(Modifier.width(5.dp)); Text("Remove Object Remover") }
                }
            }
        }
        item {
            SectionTitle("PRIVACY", "Your media stays yours")
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Your photos and recordings stay on your phone while the tools work. They're not uploaded. Speech-to-Text only records after you grant microphone access. A tool downloads only after you choose it.", style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = onPrivacyPolicy, contentPadding = PaddingValues(0.dp)) { Text("Read privacy policy") }
                }
            }
        }
        item { SectionTitle("ABOUT", "Dapple") }
        item { Text("Version 1.0 · Creative AI, made personal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { TextButton(onClick = onLicenses) { Text("Model licenses and notices") } }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("Remove Background Remover?") },
        text = { Text("You can download it again from Explore whenever you need it.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } },
    )
    if (confirmDeleteUpscaler) AlertDialog(
        onDismissRequest = { confirmDeleteUpscaler = false }, title = { Text("Remove Photo Enhancer?") },
        text = { Text("You can download it again from Explore whenever you need it.") },
        confirmButton = { TextButton(onClick = { confirmDeleteUpscaler = false; onDeleteUpscaler() }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmDeleteUpscaler = false }) { Text("Keep it") } },
    )
    if (confirmDeleteSpeech) AlertDialog(
        onDismissRequest = { confirmDeleteSpeech = false }, title = { Text("Remove Speech Noise Remover?") },
        text = { Text("You can download it again from Explore whenever you need it.") },
        confirmButton = { TextButton(onClick = { confirmDeleteSpeech = false; onDeleteSpeech() }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmDeleteSpeech = false }) { Text("Keep it") } },
    )
    if (confirmDeleteTranscription) AlertDialog(
        onDismissRequest = { confirmDeleteTranscription = false },
        title = { Text("Remove Speech-to-Text model?") },
        text = { Text("You can download it again from Explore. Your saved transcripts won't be deleted.") },
        confirmButton = { TextButton(onClick = { confirmDeleteTranscription = false; onDeleteTranscription() }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmDeleteTranscription = false }) { Text("Keep model") } },
    )
    if (confirmDeleteObject) AlertDialog(
        onDismissRequest = { confirmDeleteObject = false }, title = { Text("Remove Object Remover?") },
        text = { Text("You can download it again from Explore whenever you need it.") },
        confirmButton = { TextButton(onClick = { confirmDeleteObject = false; onDeleteObject() }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmDeleteObject = false }) { Text("Keep it") } },
    )
}

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.clip(CircleShape).clickable(onClick = onBack).padding(9.dp))
                Text("Privacy policy", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
        }
        item { Text("Last updated: October 30, 2026", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { PolicySection("Publisher", "Dapple is published by XenEva Studio. Visit xenevastudio.github.io for information about the publisher.") }
        item { PolicySection("Your photos and recordings", "Dapple processes the photos and audio you choose on your phone. Speech-to-Text can also record audio while you use its recording screen, after you grant microphone access. The app does not upload your original media or processed results to Dapple. Photos are handled in memory. Recordings and audio results may be kept temporarily in the app's cache until Android clears it or you remove the app. Nothing is saved to your photo library or documents unless you choose Save.") }
        item { PolicySection("Tool downloads", "When you choose to download a tool, Dapple connects over HTTPS to Hugging Face to download its files. The download service and your network provider may receive standard connection information, such as your IP address, and handle it under their own privacy policies. Your photos and recordings are not included in these download requests. Downloaded tools are stored in Dapple's private app storage and can be removed in Settings or by uninstalling the app.") }
        item { PolicySection("Files you select", "You choose files using Android's system file and photo pickers. Dapple only reads the files you select for the feature you use. Dapple does not request broad access to your photo library or audio files.") }
        item { PolicySection("Data collection", "Dapple does not require an account and this app version does not include advertising or analytics features. Model downloads contact Hugging Face as described above. Review the Google Play Data safety section for the app's current disclosures.") }
        item { PolicySection("Your choices", "You can remove downloaded tools in Settings. You can delete saved photos and audio using your phone's Photos or Files app. Uninstalling Dapple removes its private app files, including downloaded tools and temporary audio results.") }
        item { PolicySection("Children", "Dapple is not designed specifically for children and does not knowingly collect personal information from children.") }
        item { PolicySection("Changes and contact", "This policy may be updated when Dapple's features or data practices change. For privacy questions, contact the developer using the email address on Dapple's Google Play listing.") }
        item { TextButton(onClick = { uriHandler.openUri("https://xenevastudio.github.io/privacy.html") }) { Text("View privacy policy on XenEva Studio website") } }
    }
}

@Composable
fun LicenseNoticesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.clip(CircleShape).clickable(onClick = onBack).padding(9.dp))
                Text("Model licenses", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
        }
        item { Text("Open each model project to review its license and attribution terms. Downloaded model files may have separate distribution terms.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { LicenseLink("Background Remover", "Apache 2.0 · ORMBG LiteRT", "https://huggingface.co/litert-community/ormbg-LiteRT", uriHandler::openUri) }
        item { LicenseLink("Photo Enhancer", "BSD 3-Clause · Real-ESRGAN", "https://github.com/xinntao/Real-ESRGAN/blob/master/LICENSE", uriHandler::openUri) }
        item { LicenseLink("Speech Noise Remover", "Apache 2.0 · DPDFNet", "https://huggingface.co/Ceva-IP/DPDFNet", uriHandler::openUri) }
        item { LicenseLink("Speech-to-Text model", "MIT · OpenAI Whisper", "https://github.com/openai/whisper/blob/main/LICENSE", uriHandler::openUri) }
        item { LicenseLink("Speech-to-Text runtime", "Apache 2.0 · sherpa-onnx", "https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE", uriHandler::openUri) }
        item { LicenseLink("Object Remover", "Apache 2.0 · LaMa-Dilated", "https://huggingface.co/qualcomm/LaMa-Dilated", uriHandler::openUri) }
        item { Text("The downloadable files are hosted separately from these upstream projects. Their publisher must have the right to distribute those files under the applicable terms.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun LicenseLink(title: String, subtitle: String, uri: String, onOpen: (String) -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onOpen(uri) }, contentPadding = PaddingValues(0.dp)) { Text("View license and source") }
        }
    }
}

@Composable
private fun PolicySection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PrivacyNote() {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Lock, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Your files stay on your phone while available tools work", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CompatibilityNote() {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            "Your phone or file may have limits. Some files may not work, and results can vary.",
            Modifier.fillMaxWidth().padding(13.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResolutionNote() {
    Text(
        "This large photo was resized to fit your phone's memory. Your original stays unchanged; the saved result uses the edited size.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EmptyState(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Rounded.CloudDownload, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CutoutResultCard(source: Bitmap?, result: Bitmap, onError: (String) -> Unit, onSaved: () -> Unit) {
    ImageResultCard("Background removed", source, result, onError, onSaved, "background-removed")
}

@Composable
fun ImageResultCard(
    title: String,
    source: Bitmap?,
    result: Bitmap,
    onError: (String) -> Unit,
    onSaved: () -> Unit,
    fileNamePrefix: String = "dapple-image",
) {
    val context = LocalContext.current
    var showOriginal by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("RESULT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.2.sp); Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = { showOriginal = !showOriginal }) { Text(if (showOriginal) "Show result" else "Compare") }
        }
        Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(20.dp)).background(Color.White), Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val tile = 18.dp.toPx()
                for (y in 0..(size.height / tile).toInt()) for (x in 0..(size.width / tile).toInt()) {
                    drawRect(if ((x + y) % 2 == 0) Color(0xFFE8ECE8) else Color.White, Offset(x * tile, y * tile), Size(tile, tile))
                }
            }
            val bitmap = if (showOriginal) source else result
            if (bitmap != null) ComposeImage(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale).pointerInput(Unit) { detectTransformGestures { _, _, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 4f) } }, contentScale = ContentScale.Fit)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                runCatching { ImageExporter.save(context, result, fileNamePrefix) }.onSuccess { onSaved() }.onFailure { onError("Couldn't save this photo. Please try again.") }
            }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Text("Save PNG") }
            OutlinedButton(onClick = {
                runCatching {
                    val uri = ImageExporter.save(context, result, fileNamePrefix)
                    val send = Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    context.startActivity(Intent.createChooser(send, "Share image"))
                    onSaved()
                }.onFailure { onError("Couldn't share this photo. Please try again.") }
            }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(6.dp)); Text("Share") }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "0 MB"
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000f)
    else -> "$bytes B"
}
