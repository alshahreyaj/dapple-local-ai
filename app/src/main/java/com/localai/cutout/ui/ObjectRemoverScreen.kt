package com.localai.cutout.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localai.cutout.catalog.FeatureCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

private data class MaskStroke(val points: List<Offset>, val width: Float, val erase: Boolean)
private enum class CanvasMode { BRUSH, PAN }

@Composable
fun ObjectRemoverScreen(
    state: AppState,
    onBack: () -> Unit,
    onChoosePhoto: () -> Unit,
    onRemoveObjects: (Bitmap) -> Unit,
    onCancel: () -> Unit,
    onResetResult: () -> Unit,
    onSaved: () -> Unit,
    onError: (String) -> Unit,
    onPreparePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photo = state.objectSource ?: state.source
    LaunchedEffect(photo, state.objectSource) { if (state.objectSource == null && photo != null) onPreparePhoto() }
    var mode by remember(photo) { mutableStateOf(CanvasMode.BRUSH) }
    var erase by remember(photo) { mutableStateOf(false) }
    var brushSize by remember(photo) { mutableFloatStateOf(36f) }
    var showMask by remember(photo) { mutableStateOf(true) }
    var zoom by remember(photo) { mutableFloatStateOf(1f) }
    var pan by remember(photo) { mutableStateOf(Offset.Zero) }
    val strokes = remember(photo) { mutableStateListOf<MaskStroke>() }
    val redo = remember(photo) { mutableStateListOf<MaskStroke>() }
    var canvasSize by remember(photo) { mutableStateOf(IntSize.Zero) }
    var maskPreparing by remember(photo) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentZoom by rememberUpdatedState(zoom)
    val currentPan by rememberUpdatedState(pan)
    val currentBrushSize by rememberUpdatedState(brushSize)
    val currentErase by rememberUpdatedState(erase)

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.clip(CircleShape).clickable(onClick = onBack).padding(9.dp))
            Column {
                Text("DAPPLE PHOTO TOOL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.1.sp)
                Text("Object Remover", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
        }
        Text("Paint over what you want to remove.", style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Text("Keep the selection close to the object. Dapple fills the marked area on your phone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.objectPhotoDownscaled) {
            Text("This large photo was resized for editing. Your original photo stays unchanged, and the saved result uses the edited size.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (state.objectResult != null && photo != null) {
            ImageResultCard("Object removed", photo, state.objectResult, onError, onSaved, "object-removed")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onResetResult, modifier = Modifier.weight(1f)) { Text("Try another selection") }
                OutlinedButton(onClick = onChoosePhoto, modifier = Modifier.weight(1f)) { Text("Choose another photo") }
            }
        } else if (photo == null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(FeatureCatalog.all.first { it.id == "object-remover" }.previewResource!!),
                        contentDescription = "Object Remover preview",
                        modifier = Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop,
                    )
                    Text("Choose a photo to start", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Button(onClick = onChoosePhoto, modifier = Modifier.fillMaxWidth()) { Text("Choose a photo") }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = mode == CanvasMode.BRUSH, onClick = { mode = CanvasMode.BRUSH }, label = { Text("Brush") }, leadingIcon = { androidx.compose.material3.Icon(Icons.Rounded.Brush, null, Modifier.size(18.dp)) })
                FilterChip(selected = mode == CanvasMode.PAN, onClick = { mode = CanvasMode.PAN }, label = { Text("Pan & zoom") })
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                TextButton(onClick = { strokes.clear(); redo.clear(); zoom = 1f; pan = Offset.Zero }, enabled = strokes.isNotEmpty() || zoom != 1f || pan != Offset.Zero) { Text("Reset") }
            }
            Box(Modifier.fillMaxWidth().height(330.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFE8ECE8)).onSizeChanged { canvasSize = it }) {
                val imageBitmap = remember(photo) { photo.asImageBitmap() }
                Canvas(
                    Modifier.fillMaxSize().pointerInput(photo, mode, brushSize, erase, canvasSize) {
                        if (mode == CanvasMode.PAN) {
                            detectTransformGestures { _, gesturePan, gestureZoom, _ ->
                                val nextZoom = (currentZoom * gestureZoom).coerceIn(1f, 5f)
                                val fit = min(canvasSize.width.toFloat() / photo.width, canvasSize.height.toFloat() / photo.height)
                                val maxPanX = ((photo.width * fit * nextZoom - canvasSize.width) / 2f).coerceAtLeast(0f)
                                val maxPanY = ((photo.height * fit * nextZoom - canvasSize.height) / 2f).coerceAtLeast(0f)
                                pan = Offset((currentPan.x + gesturePan.x).coerceIn(-maxPanX, maxPanX), (currentPan.y + gesturePan.y).coerceIn(-maxPanY, maxPanY))
                                zoom = nextZoom
                            }
                        } else {
                            detectDragGestures(
                                onDragStart = { point ->
                                    val mapped = mapToPhoto(point, canvasSize, photo.width, photo.height, currentZoom, currentPan)
                                    if (mapped != null) {
                                        strokes += MaskStroke(listOf(mapped), currentBrushSize, currentErase)
                                        redo.clear()
                                    }
                                },
                                onDrag = { change, _ ->
                                    val mapped = mapToPhoto(change.position, canvasSize, photo.width, photo.height, currentZoom, currentPan)
                                    if (mapped != null && strokes.isNotEmpty()) {
                                        val last = strokes.last()
                                        strokes[strokes.lastIndex] = last.copy(points = last.points + mapped)
                                    }
                                    change.consume()
                                },
                            )
                        }
                    },
                ) {
                    val fit = min(size.width / photo.width, size.height / photo.height)
                    val displayedScale = fit * zoom
                    val imageW = photo.width * displayedScale
                    val imageH = photo.height * displayedScale
                    val left = (size.width - imageW) / 2f + pan.x
                    val top = (size.height - imageH) / 2f + pan.y
                    drawImage(imageBitmap, dstOffset = IntOffset(left.toInt(), top.toInt()), dstSize = IntSize(imageW.toInt().coerceAtLeast(1), imageH.toInt().coerceAtLeast(1)))
                    if (showMask) {
                        val nativeCanvas = drawContext.canvas.nativeCanvas
                        val layer = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, null)
                        strokes.forEach { stroke ->
                        val path = Path()
                        stroke.points.forEachIndexed { index, point ->
                            val screen = Offset(left + point.x * displayedScale, top + point.y * displayedScale)
                            if (index == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
                        }
                        val blend = if (stroke.erase) BlendMode.Clear else BlendMode.SrcOver
                        val color = if (stroke.erase) Color.Transparent else Color(0x99FF426D)
                        if (stroke.points.size == 1) {
                            val p = stroke.points.first()
                            drawCircle(color, radius = stroke.width * displayedScale / 2f, center = Offset(left + p.x * displayedScale, top + p.y * displayedScale), blendMode = blend)
                        } else drawPath(path, color, style = Stroke(stroke.width * displayedScale, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blend)
                        }
                        nativeCanvas.restoreToCount(layer)
                    }
                }
                if (mode == CanvasMode.PAN && zoom > 1.01f) Text("${(zoom * 100).toInt()}%", Modifier.align(Alignment.BottomEnd).padding(10.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = .9f), RoundedCornerShape(20.dp)).padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = !erase, onClick = { erase = false; mode = CanvasMode.BRUSH }, label = { Text("Paint") })
                FilterChip(selected = erase, onClick = { erase = true; mode = CanvasMode.BRUSH }, label = { Text("Erase") })
                FilterChip(selected = showMask, onClick = { showMask = !showMask }, label = { Text(if (showMask) "Mask shown" else "Mask hidden") })
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                TextButton(onClick = { if (strokes.isNotEmpty()) redo += strokes.removeAt(strokes.lastIndex) }, enabled = strokes.isNotEmpty()) { androidx.compose.material3.Icon(Icons.Rounded.Undo, "Undo") }
                TextButton(onClick = { if (redo.isNotEmpty()) strokes += redo.removeAt(redo.lastIndex) }, enabled = redo.isNotEmpty()) { androidx.compose.material3.Icon(Icons.Rounded.Redo, "Redo") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Brush", style = MaterialTheme.typography.labelMedium)
                Slider(value = brushSize, onValueChange = { brushSize = it }, valueRange = 8f..120f, modifier = Modifier.weight(1f))
                Text("${brushSize.toInt()} px", style = MaterialTheme.typography.labelSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onChoosePhoto, enabled = !state.objectProcessing, modifier = Modifier.weight(1f)) { Text("Change photo") }
                Button(
                    onClick = {
                        val currentPhoto = photo
                        val savedStrokes = strokes.toList()
                        maskPreparing = true
                        scope.launch {
                            val mask = try { withContext(Dispatchers.IO) { createMask(currentPhoto, savedStrokes) } }
                            catch (_: OutOfMemoryError) { null }
                            catch (_: Exception) { null }
                            maskPreparing = false
                            if (mask != null) onRemoveObjects(mask) else onError("Couldn't prepare this selection. Try a smaller photo.")
                        }
                    },
                    enabled = !maskPreparing && !state.objectProcessing && !state.objectDownloading && strokes.any { !it.erase },
                    modifier = Modifier.weight(1f),
                ) { Text(if (maskPreparing) "Preparing selection…" else "Remove object") }
            }
        }

        if (state.objectDownloading) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (state.objectProgress >= 0) "Downloading Object Remover · ${state.objectProgress}%" else "Downloading Object Remover…", style = MaterialTheme.typography.bodyMedium)
                if (state.objectProgress >= 0) LinearProgressIndicator({ state.objectProgress / 100f }, Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("Cancel download") }
            }
        }
        if (state.objectProcessing) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Filling the selected area…", style = MaterialTheme.typography.bodyMedium)
                        if (state.objectTileTotal > 0) LinearProgressIndicator({ state.objectTileProgress.toFloat() / state.objectTileTotal }, Modifier.fillMaxWidth())
                        if (state.objectTileTotal > 0) Text("Area ${state.objectTileProgress} of ${state.objectTileTotal}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
        }
        if (state.objectError != null) Text(state.objectError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        CompatibilityNote()
        PrivacyNote()
    }
}

private fun mapToPhoto(point: Offset, canvas: IntSize, imageW: Int, imageH: Int, zoom: Float, pan: Offset): Offset? {
    if (canvas.width <= 0 || canvas.height <= 0) return null
    val fit = min(canvas.width.toFloat() / imageW, canvas.height.toFloat() / imageH) * zoom
    val width = imageW * fit
    val height = imageH * fit
    val left = (canvas.width - width) / 2f + pan.x
    val top = (canvas.height - height) / 2f + pan.y
    val x = (point.x - left) / fit
    val y = (point.y - top) / fit
    return if (x in 0f..imageW.toFloat() && y in 0f..imageH.toFloat()) Offset(x, y) else null
}

private fun createMask(photo: Bitmap, strokes: List<MaskStroke>): Bitmap? {
    if (strokes.none { !it.erase }) return null
    val mask = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(mask)
    strokes.forEach { stroke ->
        if (stroke.points.isEmpty()) return@forEach
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = if (stroke.erase) android.graphics.Color.TRANSPARENT else android.graphics.Color.WHITE
            style = AndroidPaint.Style.STROKE
            strokeWidth = stroke.width
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
            if (stroke.erase) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        val path = AndroidPath().apply {
            moveTo(stroke.points.first().x, stroke.points.first().y)
            if (stroke.points.size == 1) lineTo(stroke.points.first().x + .1f, stroke.points.first().y + .1f)
            else stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        canvas.drawPath(path, paint)
    }
    return mask
}
