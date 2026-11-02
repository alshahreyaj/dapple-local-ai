package com.localai.cutout.ui

import android.net.Uri
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.localai.cutout.core.document.NormalizedQuad
import java.io.File

@Composable
fun DocumentScannerScreen(
    state: AppState,
    onBack: () -> Unit,
    onPickImage: () -> Unit,
    onCameraImage: (Uri) -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onCorners: (NormalizedQuad) -> Unit,
    onSelectPage: (Int) -> Unit,
    onCrop: () -> Unit,
    onRotate: () -> Unit,
    onFilter: (String) -> Unit,
    onRecognize: () -> Unit,
    onText: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDelete: (Int) -> Unit,
    onStartOver: () -> Unit,
    onSaveText: () -> Unit,
    onSavePdf: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCamera by remember { mutableStateOf(false) }
    var confirmStartOver by remember { mutableStateOf(false) }
    var pageMenuIndex by remember { mutableIntStateOf(-1) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val allPagesText = state.documentPages.mapIndexed { i, page -> "Page ${i + 1}\n${page.text}" }.joinToString("\n\n")
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { allowed -> if (allowed) showCamera = true }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Document Scanner", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Scan, clean up and search pages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.documentPages.isNotEmpty()) {
                TextButton(onClick = { confirmStartOver = true }, enabled = !state.documentOcrBusy) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Start over")
                }
            }
        }
        if (showCamera) {
            CameraCapture(
                onCaptured = { uri -> showCamera = false; onCameraImage(uri) },
                onClose = { showCamera = false },
                modifier = Modifier.fillMaxWidth().height(430.dp),
            )
        } else if (state.documentPages.isEmpty()) {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    Icon(Icons.Rounded.Description, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Create your first scan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Capture a paper document or import an image. Scan and recognize text without uploading anything.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { permission.launch(android.Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AddAPhoto, null); Spacer(Modifier.width(8.dp)); Text("Use camera") }
                    OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Choose an image") }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(onClick = { permission.launch(android.Manifest.permission.CAMERA) }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.AddAPhoto, null); Spacer(Modifier.width(6.dp)); Text("Scan page") }
                OutlinedButton(onClick = onPickImage, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Add image") }
            }
            val index = state.documentSelectedPage.coerceIn(0, state.documentPages.lastIndex)
            val page = state.documentPages[index]
            Text("Page ${index + 1} of ${state.documentPages.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            CropEditor(page.image, page.corners, onCorners)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCrop, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(5.dp)); Text("Apply crop") }
                OutlinedButton(onClick = onRotate, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.RotateRight, null); Spacer(Modifier.width(5.dp)); Text("Rotate") }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Original", "Grayscale", "Black & white", "Contrast").forEach { filter ->
                    FilterChip(selected = page.filter == filter, onClick = { onFilter(filter) }, label = { Text(filter, fontSize = 11.sp) })
                }
            }
            val searchable = state.documentPages.isNotEmpty() && state.documentPages.all { it.text.isNotBlank() }
            Button(onClick = onSavePdf, modifier = Modifier.fillMaxWidth(), enabled = state.documentPages.isNotEmpty()) {
                Icon(Icons.Rounded.PictureAsPdf, null)
                Spacer(Modifier.width(8.dp))
                Text(if (searchable) "Save searchable PDF" else "Save PDF")
            }
            if (!state.documentOcrInstalled) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Offline text recognition", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Download the English and German text reader once. It stays on your phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.documentOcrDownloading) {
                            LinearProgressIndicator(progress = { state.documentOcrProgress / 100f }, modifier = Modifier.fillMaxWidth())
                            Text("${state.documentOcrProgress}% downloaded", style = MaterialTheme.typography.labelSmall)
                            OutlinedButton(onClick = onCancelDownload, modifier = Modifier.fillMaxWidth()) { Text("Cancel download") }
                        } else Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("Download · 12.9 MB") }
                    }
                }
            } else {
                Button(onClick = onRecognize, enabled = !state.documentOcrBusy, modifier = Modifier.fillMaxWidth()) {
                    if (state.documentOcrBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Description, null)
                    Spacer(Modifier.width(8.dp)); Text(if (state.documentOcrBusy) "Reading page…" else "Recognize text")
                }
            }
            if (state.documentOcrError != null) Text(state.documentOcrError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (state.documentPages.isNotEmpty()) {
                Text("Pages · ${state.documentPages.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(state.documentPages) { pageIndex, item ->
                        Card(onClick = { pageMenuIndex = -1; onSelectPage(pageIndex) }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (pageIndex == index) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.width(138.dp).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Image(item.image.asImageBitmap(), null, Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Page ${pageIndex + 1}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                                    Box {
                                        IconButton(onClick = { pageMenuIndex = pageIndex }) {
                                            Icon(Icons.Rounded.MoreVert, contentDescription = "Page ${pageIndex + 1} options")
                                        }
                                        DropdownMenu(expanded = pageMenuIndex == pageIndex, onDismissRequest = { pageMenuIndex = -1 }) {
                                            DropdownMenuItem(
                                                text = { Text("Move earlier") },
                                                leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null) },
                                                enabled = pageIndex > 0,
                                                onClick = { pageMenuIndex = -1; onMove(pageIndex, -1) },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Move later") },
                                                leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null) },
                                                enabled = pageIndex < state.documentPages.lastIndex,
                                                onClick = { pageMenuIndex = -1; onMove(pageIndex, 1) },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete page") },
                                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                                                onClick = { pageMenuIndex = -1; onDelete(pageIndex) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text("Text", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = page.text,
                onValueChange = onText,
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 220.dp),
                maxLines = 8,
                placeholder = { Text("Recognized text will appear here. You can edit it.") },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f), enabled = allPagesText.isNotBlank()) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(4.dp)); Text("Share") }
                OutlinedButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(allPagesText)) }, modifier = Modifier.weight(1f), enabled = allPagesText.isNotBlank()) { Icon(Icons.Rounded.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Copy") }
            }
            OutlinedButton(onClick = onSaveText, modifier = Modifier.fillMaxWidth(), enabled = state.documentPages.any { it.text.isNotBlank() }) { Icon(Icons.Rounded.Description, null); Spacer(Modifier.width(5.dp)); Text("Save TXT") }
        }
    }
    if (confirmStartOver) AlertDialog(
        onDismissRequest = { confirmStartOver = false },
        title = { Text("Start a new scan?") },
        text = { Text("This clears the pages and recognized text from the current scan. Your downloaded OCR model stays ready.") },
        confirmButton = {
            TextButton(onClick = { confirmStartOver = false; showCamera = false; onStartOver() }) { Text("Start over") }
        },
        dismissButton = { TextButton(onClick = { confirmStartOver = false }) { Text("Keep scan") } },
    )
}

@Composable
private fun CropEditor(bitmap: Bitmap, quad: NormalizedQuad, onQuad: (NormalizedQuad) -> Unit) {
    var activeCorner by remember { mutableIntStateOf(-1) }
    val currentQuad by rememberUpdatedState(quad)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val aspect = bitmap.width.toFloat() / bitmap.height
        val h = maxWidth / aspect
        Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(20.dp)).background(Color.Black)) {
            Image(bitmap.asImageBitmap(), "Document page crop", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        activeCorner = currentQuad.points.indices.minByOrNull { i ->
                            val p = currentQuad.points[i]; (Offset(p.first * size.width, p.second * size.height) - pos).getDistance()
                        } ?: -1
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        if (activeCorner in currentQuad.points.indices) {
                            val points = currentQuad.points.toMutableList()
                            val old = points[activeCorner]
                            points[activeCorner] = (old.first + drag.x / size.width).coerceIn(0f, 1f) to (old.second + drag.y / size.height).coerceIn(0f, 1f)
                            onQuad(NormalizedQuad(points))
                        }
                    },
                    onDragEnd = { activeCorner = -1 },
                )
            }) {
                val pts = quad.points.map { Offset(it.first * size.width, it.second * size.height) }
                if (pts.size == 4) {
                    val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                    drawPath(path, Color(0xFFBEE8D5).copy(alpha = .18f))
                    drawPath(path, Color(0xFF70D4A4), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
                    pts.forEach { drawCircle(Color.White, radius = 8.dp.toPx(), center = it); drawCircle(Color(0xFF287A5A), radius = 5.dp.toPx(), center = it) }
                }
            }
        }
    }
}

@Composable
private fun CameraCapture(onCaptured: (Uri) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(owner) { onDispose { runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)),
            factory = { ctx ->
                val view = PreviewView(ctx)
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    runCatching {
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                        val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build().also { capture -> view.display?.let { capture.targetRotation = it.rotation } }
                        provider.unbindAll(); provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                        capture = imageCapture
                    }.onFailure { error = "Camera couldn't start. You can choose an image instead." }
                }, ContextCompat.getMainExecutor(ctx))
                view
            },
        )
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Close camera") }
            Button(onClick = {
                val active = capture ?: return@Button
                val file = File(context.cacheDir, "dapple-scan-${System.currentTimeMillis()}.jpg")
                active.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) { onCaptured(Uri.fromFile(file)) }
                    override fun onError(exception: ImageCaptureException) { error = "Couldn't capture this page. Try again." }
                })
            }, modifier = Modifier.weight(1f), enabled = capture != null) { Icon(Icons.Rounded.AddAPhoto, null); Spacer(Modifier.width(6.dp)); Text("Capture page") }
        }
    }
}
