package com.localai.cutout.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.localai.cutout.core.inference.BgRemover
import com.localai.cutout.core.image.ImageProcessor
import com.localai.cutout.core.image.ImageExporter
import com.localai.cutout.core.model.ModelDownloadWorker
import com.localai.cutout.core.model.ModelManager
import com.localai.cutout.core.model.ModelSpecs
import com.localai.cutout.core.model.PhotoRestorationModelStore
import com.localai.cutout.core.inference.RealEsrganUpscaler
import com.localai.cutout.core.inference.DpdfnetSpeechDenoiser
import com.localai.cutout.core.inference.LamaObjectRemover
import com.localai.cutout.core.inference.PhotoRestorationPipeline
import com.localai.cutout.core.inference.WhisperSpeechToText
import com.localai.cutout.core.inference.WhisperLanguages
import com.localai.cutout.core.inference.TranscriptTimeMark
import com.localai.cutout.core.audio.WavAudio
import com.localai.cutout.core.document.DocumentOcrEngine
import com.localai.cutout.core.document.DocumentImageProcessor
import com.localai.cutout.core.document.NormalizedQuad
import com.localai.cutout.core.document.OcrLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Build
import android.graphics.Color

data class AppState(
    val installed: Boolean = false,
    val downloading: Boolean = false,
    val downloadProgress: Int = 0,
    val status: String? = null,
    val busy: Boolean = false,
    val source: Bitmap? = null,
    val backgroundSource: Bitmap? = null,
    val backgroundPhotoDownscaled: Boolean = false,
    val upscalerSource: Bitmap? = null,
    val upscalerPhotoDownscaled: Boolean = false,
    val result: Bitmap? = null,
    val upscalerInstalled: Boolean = false,
    val upscalerDownloading: Boolean = false,
    val upscalerProgress: Int = 0,
    val upscalerBytes: Long = 0L,
    val upscaleResult: Bitmap? = null,
    val upscalerBackend: String? = null,
    val upscalerTileProgress: Int = 0,
    val upscalerTileTotal: Int = 0,
    val upscalerError: String? = null,
    val savedUri: Uri? = null,
    val settings: Boolean = false,
    val modelBytes: Long = 0L,
    val themePreference: String = "system",
    val speechInstalled: Boolean = false,
    val speechDownloading: Boolean = false,
    val speechProgress: Int = 0,
    val speechBytes: Long = 0L,
    val speechError: String? = null,
    val speechBusy: Boolean = false,
    val speechTileProgress: Int = 0,
    val speechTileTotal: Int = 0,
    val audioSource: Uri? = null,
    val audioName: String? = null,
    val enhancedAudioPath: String? = null,
    val audioPlaying: String? = null,
    val objectInstalled: Boolean = false,
    val objectDownloading: Boolean = false,
    val objectProgress: Int = 0,
    val objectBytes: Long = 0L,
    val objectError: String? = null,
    val objectProcessing: Boolean = false,
    val objectTileProgress: Int = 0,
    val objectTileTotal: Int = 0,
    val objectSource: Bitmap? = null,
    val objectResult: Bitmap? = null,
    val objectPhotoDownscaled: Boolean = false,
    val transcriptionInstalled: Boolean = false,
    val transcriptionDownloading: Boolean = false,
    val transcriptionProgress: Int = 0,
    val transcriptionBytes: Long = 0L,
    val transcriptionError: String? = null,
    val transcriptionBusy: Boolean = false,
    val transcriptionStage: String? = null,
    val transcriptionSource: Uri? = null,
    val transcriptionName: String? = null,
    val transcriptionLanguageSelection: String = "auto",
    val transcriptText: String = "",
    val transcriptLanguage: String? = null,
    val transcriptMarks: List<TranscriptTimeMark> = emptyList(),
    val transcriptionRecording: Boolean = false,
    val restorationScratchInstalled: Boolean = false,
    val restorationLamaInstalled: Boolean = false,
    val restorationColorizerInstalled: Boolean = false,
    val restorationEnhancerInstalled: Boolean = false,
    val restorationDownloading: Boolean = false,
    val restorationProgress: Int = 0,
    val restorationBytes: Long = 0L,
    val restorationError: String? = null,
    val restorationProcessing: Boolean = false,
    val restorationStage: String? = null,
    val restorationTileProgress: Int = 0,
    val restorationTileTotal: Int = 0,
    val restorationSource: Bitmap? = null,
    val restorationMaskPreview: Bitmap? = null,
    val restorationResult: Bitmap? = null,
    val restorationPhotoDownscaled: Boolean = false,
    val documentOcrInstalled: Boolean = false,
    val documentOcrDownloading: Boolean = false,
    val documentOcrProgress: Int = 0,
    val documentOcrBytes: Long = 0L,
    val documentOcrError: String? = null,
    val documentOcrBusy: Boolean = false,
    val documentPages: List<DocumentPage> = emptyList(),
    val documentSelectedPage: Int = 0,
)

data class DocumentPage(
    val image: Bitmap,
    val originalImage: Bitmap = image,
    val corners: NormalizedQuad = NormalizedQuad.full(),
    val filter: String = "Original",
    val text: String = "",
    val lines: List<OcrLine> = emptyList(),
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private enum class PhotoTarget { GENERAL, BACKGROUND, UPSCALER, OBJECT, RESTORATION }
    private val manager = ModelManager(app)
    private val upscalerManager = ModelManager(app, ModelSpecs.imageUpscaler)
    private val speechManager = ModelManager(app, ModelSpecs.speechNoiseRemover)
    private val objectManager = ModelManager(app, ModelSpecs.objectRemover)
    private val transcriptionManager = ModelManager(app, ModelSpecs.speechToText)
    private val documentOcrManager = ModelManager(app, ModelSpecs.documentOcr)
    private val restorationStore = PhotoRestorationModelStore(app)
    private val work = WorkManager.getInstance(app)
    private val preferences = app.getSharedPreferences("locora_settings", Application.MODE_PRIVATE)
    private var remover: BgRemover? = null
    private var upscaler: RealEsrganUpscaler? = null
    private var speechDenoiser: DpdfnetSpeechDenoiser? = null
    private var objectRemover: LamaObjectRemover? = null
    private var transcriptionEngine: WhisperSpeechToText? = null
    private var transcriptionEngineLanguage: String? = null
    private var documentOcrEngine: DocumentOcrEngine? = null
    private var documentDownloadJob: kotlinx.coroutines.Job? = null
    private var restorationPipeline: PhotoRestorationPipeline? = null
    private var restorationDownloadJob: kotlinx.coroutines.Job? = null
    private var transcriptionJob: kotlinx.coroutines.Job? = null
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: java.io.File? = null
    private var mediaPlayer: MediaPlayer? = null
    private var processAfterDownload = false
    private val _state = MutableStateFlow(AppState(
        themePreference = preferences.getString("theme", "system") ?: "system",
        transcriptionLanguageSelection = preferences.getString("transcription_language", "auto") ?: "auto",
    ))
    val state = _state.asStateFlow()

    init {
        // Remove retired model files so the renamed Photo Enhancer model is downloaded fresh.
        listOf(
            "real_esrgan_x4plus.tflite", "real_esrgan_x4plus.tflite.download.part", "real_esrgan_x4plus.tflite.installing",
            "mossformergan.onnx", "mossformergan.onnx.download.part", "mossformergan.onnx.installing",
        )
            .forEach { getApplication<Application>().filesDir.resolve(it).delete() }
        viewModelScope.launch(Dispatchers.IO) {
            val ready = manager.installed()
            val bytes = if (ready) manager.sizeBytes() else 0L
            val upscaleReady = upscalerManager.installed()
            val speechReady = speechManager.installed()
            val objectReady = objectManager.installed()
            val transcriptionReady = transcriptionManager.installed()
            val scratchReady = restorationStore.scratchInstalled()
            val lamaReady = restorationStore.lamaInstalled()
            val colorizerReady = restorationStore.colorizerInstalled()
            val restorationEnhancerReady = restorationStore.enhancerInstalled()
            val documentReady = documentOcrManager.installed()
            _state.update { it.copy(installed = ready, modelBytes = bytes, upscalerInstalled = upscaleReady, upscalerBytes = if (upscaleReady) upscalerManager.sizeBytes() else 0L, speechInstalled = speechReady, speechBytes = if (speechReady) speechManager.sizeBytes() else 0L, objectInstalled = objectReady, objectBytes = if (objectReady) objectManager.sizeBytes() else 0L, transcriptionInstalled = transcriptionReady, transcriptionBytes = if (transcriptionReady) transcriptionManager.sizeBytes() else 0L, restorationScratchInstalled = scratchReady, restorationLamaInstalled = lamaReady, restorationColorizerInstalled = colorizerReady, restorationEnhancerInstalled = restorationEnhancerReady, restorationBytes = restorationStore.scratchBytes() + restorationStore.lamaBytes() + restorationStore.colorizerBytes() + restorationStore.enhancerBytes(), documentOcrInstalled = documentReady, documentOcrBytes = if (documentReady) documentOcrManager.sizeBytes() else 0L) }
        }
    }

    fun choose(uri: Uri) = loadPhoto(uri, PhotoTarget.GENERAL)
    fun chooseBackgroundPhoto(uri: Uri) = loadPhoto(uri, PhotoTarget.BACKGROUND)
    fun chooseUpscalerPhoto(uri: Uri) = loadPhoto(uri, PhotoTarget.UPSCALER)
    fun chooseObjectPhoto(uri: Uri) = loadPhoto(uri, PhotoTarget.OBJECT)
    fun chooseRestorationPhoto(uri: Uri) = loadPhoto(uri, PhotoTarget.RESTORATION)

    fun downloadDocumentOcr() {
        if (_state.value.documentOcrDownloading || _state.value.documentOcrInstalled) return
        val spec = ModelSpecs.documentOcr
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>().setInputData(androidx.work.workDataOf(ModelDownloadWorker.KEY_MODEL_ID to spec.id)).build()
        work.enqueueUniqueWork("model:${spec.id}", ExistingWorkPolicy.REPLACE, request)
        _state.update { it.copy(documentOcrDownloading = true, documentOcrProgress = 0, documentOcrError = null) }
        documentDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    val info = work.getWorkInfoById(request.id).get() ?: break
                    val done = info.progress.getLong(ModelDownloadWorker.KEY_DONE, 0)
                    val total = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL, spec.downloadSizeBytes)
                    when (info.state) {
                        WorkInfo.State.SUCCEEDED -> _state.update { it.copy(documentOcrInstalled = true, documentOcrBytes = documentOcrManager.sizeBytes(), documentOcrDownloading = false, documentOcrProgress = 100) }
                        WorkInfo.State.FAILED -> _state.update { it.copy(documentOcrDownloading = false, documentOcrError = info.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Couldn't download the scanner. Please retry.") }
                        WorkInfo.State.CANCELLED -> _state.update { it.copy(documentOcrDownloading = false) }
                        else -> _state.update { it.copy(documentOcrProgress = ((done * 100 / total.coerceAtLeast(1)).toInt()).coerceIn(0, 99)) }
                    }
                    if (info.state.isFinished) break
                    delay(350)
                }
            } catch (e: Exception) { _state.update { it.copy(documentOcrDownloading = false, documentOcrError = e.message ?: "Couldn't check download status") } }
        }
    }

    fun cancelDocumentOcrDownload() { work.cancelUniqueWork("model:${ModelSpecs.documentOcr.id}"); documentDownloadJob?.cancel(); _state.update { it.copy(documentOcrDownloading = false) } }

    fun deleteDocumentOcrModel() {
        val engine = documentOcrEngine; documentOcrEngine = null
        _state.update { it.copy(documentOcrInstalled = false, documentOcrBytes = 0L, documentOcrError = null, status = "Document Scanner model removed") }
        viewModelScope.launch(Dispatchers.IO) { runCatching { engine?.close() }; documentOcrManager.delete() }
    }

    fun addDocumentPage(uri: Uri) {
        if (_state.value.documentOcrBusy) return
        _state.update { it.copy(documentOcrBusy = true, documentOcrError = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val image = ImageProcessor.decodeForObjectRemoval(getApplication(), uri)
                val quad = DocumentImageProcessor.detectCorners(image)
                DocumentPage(image = image, originalImage = image, corners = quad)
            } }.onSuccess { page -> _state.update { it.copy(documentPages = it.documentPages + page, documentSelectedPage = it.documentPages.size, documentOcrBusy = false, documentOcrError = null) } }
                .onFailure { _state.update { it.copy(documentOcrBusy = false, documentOcrError = "Couldn't open that image. Choose a JPG, PNG or WebP photo.") } }
            if (uri.scheme == "file") runCatching { uri.path?.let { java.io.File(it).delete() } }
        }
    }

    fun selectDocumentPage(index: Int) { if (index in _state.value.documentPages.indices) _state.update { it.copy(documentSelectedPage = index) } }
    fun setDocumentCorners(corners: NormalizedQuad) { updateSelectedDocumentPage { it.copy(corners = corners) } }
    fun setDocumentText(text: String) { updateSelectedDocumentPage { it.copy(text = text) } }
    fun setDocumentFilter(filter: String) {
        val state = _state.value; val page = state.documentPages.getOrNull(state.documentSelectedPage) ?: return
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { DocumentImageProcessor.filter(page.originalImage, filter) }.onSuccess { filtered ->
                // image is the current page bitmap; keep filter state without discarding OCR text.
                val next = page.copy(image = filtered, filter = filter)
                _state.update { latest -> if (latest.documentSelectedPage == state.documentSelectedPage) latest.copy(documentPages = latest.documentPages.toMutableList().also { it[state.documentSelectedPage] = next }) else latest }
            }.onFailure { _state.update { it.copy(documentOcrError = "Couldn't apply that page filter.") } }
        }
    }
    fun cropDocumentPage() {
        val state = _state.value; val page = state.documentPages.getOrNull(state.documentSelectedPage) ?: return
        _state.update { it.copy(documentOcrBusy = true, documentOcrError = null) }
        viewModelScope.launch(Dispatchers.Default) { runCatching {
            val cropped = DocumentImageProcessor.warp(page.originalImage, page.corners)
            cropped to DocumentImageProcessor.filter(cropped, page.filter)
        }.onSuccess { (original, filtered) -> updateDocumentPage(state.documentSelectedPage) { it.copy(image = filtered, originalImage = original, corners = NormalizedQuad.full(), lines = emptyList(), text = "") }; _state.update { it.copy(documentOcrBusy = false) } }.onFailure { _state.update { it.copy(documentOcrBusy = false, documentOcrError = "Couldn't correct the page. Adjust the corners and retry.") } } }
    }
    fun rotateDocumentPage() {
        val index = _state.value.documentSelectedPage; val page = _state.value.documentPages.getOrNull(index) ?: return
        val matrix = android.graphics.Matrix().apply { postRotate(90f) }
        val rotatedOriginal = Bitmap.createBitmap(page.originalImage, 0, 0, page.originalImage.width, page.originalImage.height, matrix, true)
        val rotated = DocumentImageProcessor.filter(rotatedOriginal, page.filter)
        updateDocumentPage(index) { it.copy(image = rotated, originalImage = rotatedOriginal, corners = NormalizedQuad.full(), text = "", lines = emptyList()) }
    }
    fun moveDocumentPage(index: Int, delta: Int) {
        val pages = _state.value.documentPages.toMutableList(); val to = index + delta
        if (index !in pages.indices || to !in pages.indices) return
        val page = pages.removeAt(index); pages.add(to, page)
        _state.update { it.copy(documentPages = pages, documentSelectedPage = to) }
    }
    fun deleteDocumentPage(index: Int) {
        val pages = _state.value.documentPages.toMutableList(); if (index !in pages.indices) return
        pages.removeAt(index); _state.update { it.copy(documentPages = pages, documentSelectedPage = (index - 1).coerceAtLeast(0)) }
    }

    fun startNewDocumentScan() {
        if (_state.value.documentOcrBusy) return
        val oldPages = _state.value.documentPages
        _state.update { it.copy(documentPages = emptyList(), documentSelectedPage = 0, documentOcrError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            delay(100)
            oldPages.flatMap { listOf(it.image, it.originalImage) }.distinct().forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }
    fun recognizeDocumentPage() {
        val state = _state.value; val page = state.documentPages.getOrNull(state.documentSelectedPage) ?: return
        if (!state.documentOcrInstalled || state.documentOcrBusy) return
        _state.update { it.copy(documentOcrBusy = true, documentOcrError = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val engine = documentOcrEngine ?: DocumentOcrEngine(getApplication(), java.io.File(documentOcrManager.path())).also { documentOcrEngine = it }
                engine.scan(page.image)
            } }.onSuccess { result -> updateDocumentPage(state.documentSelectedPage) { it.copy(text = result.text, lines = result.lines) }; _state.update { it.copy(documentOcrBusy = false) } }
                .onFailure { error ->
                    val message = if (error is OutOfMemoryError || error.message.orEmpty().contains("memory", true)) {
                        "This page is too large for this phone to read. Try a smaller image."
                    } else "Couldn't read text from this page. Try a clearer, well-lit photo."
                    _state.update { it.copy(documentOcrBusy = false, documentOcrError = message) }
                }
        }
    }
    private fun updateSelectedDocumentPage(transform: (DocumentPage) -> DocumentPage) { val index = _state.value.documentSelectedPage; updateDocumentPage(index, transform) }
    private fun updateDocumentPage(index: Int, transform: (DocumentPage) -> DocumentPage) { _state.update { state -> if (index !in state.documentPages.indices) state else state.copy(documentPages = state.documentPages.toMutableList().also { it[index] = transform(it[index]) }) } }

    fun saveDocumentText(uri: Uri) {
        val text = _state.value.documentPages.mapIndexed { i, page -> if (_state.value.documentPages.size > 1) "Page ${i + 1}\n${page.text}" else page.text }.joinToString("\n\n")
        viewModelScope.launch(Dispatchers.IO) { runCatching { getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) } ?: error("Couldn't create the text file") }.onFailure { _state.update { s -> s.copy(documentOcrError = "Couldn't save the text file.") } } }
    }

    fun saveDocumentPdf(uri: Uri) {
        val pages = _state.value.documentPages
        if (pages.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val resolver = getApplication<Application>().contentResolver
                resolver.openOutputStream(uri)?.use { stream ->
                    val pdf = PdfDocument()
                    pages.forEachIndexed { index, page ->
                        val info = PdfDocument.PageInfo.Builder(page.image.width.coerceAtMost(14400), page.image.height.coerceAtMost(14400), index + 1).create()
                        val pdfPage = pdf.startPage(info)
                        val canvas = pdfPage.canvas
                        canvas.drawBitmap(page.image, null, android.graphics.Rect(0, 0, info.pageWidth, info.pageHeight), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                        // Tiny transparent searchable text layer positioned over the scanned words.
                        val scaleX = info.pageWidth.toFloat() / page.image.width; val scaleY = info.pageHeight.toFloat() / page.image.height
                        val editedLines = page.text.lines()
                        page.lines.forEachIndexed { lineIndex, line ->
                            val value = editedLines.getOrNull(lineIndex).orEmpty()
                            if (value.isNotBlank()) {
                                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 1; textSize = (line.bounds.height * scaleY).coerceAtLeast(1f); textScaleX = 1f }
                                canvas.drawText(value, line.bounds.left * scaleX, (line.bounds.bottom * scaleY).toFloat(), p)
                            }
                        }
                        // Keep newly added edited lines searchable when they have no original OCR box.
                        if (editedLines.size > page.lines.size && page.text.isNotBlank()) {
                            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 1; textSize = 1f }
                            editedLines.drop(page.lines.size).forEachIndexed { row, line -> canvas.drawText(line, 1f, (row + 1).toFloat(), p) }
                        }
                        pdf.finishPage(pdfPage)
                    }
                    pdf.writeTo(stream); pdf.close()
                } ?: error("Couldn't create the PDF")
            }.onFailure { _state.update { s -> s.copy(documentOcrError = "Couldn't save the PDF. Please try again.") } }
        }
    }

    fun prepareObjectPhoto() = prepareBoundedPhoto(PhotoTarget.OBJECT, ImageProcessor::fitForObjectRemoval)
    fun prepareBackgroundPhoto() = prepareBoundedPhoto(PhotoTarget.BACKGROUND, ImageProcessor::fitForBackgroundRemoval)
    fun prepareUpscalerPhoto() = prepareBoundedPhoto(PhotoTarget.UPSCALER, ImageProcessor::fitForUpscaling)
    fun prepareRestorationPhoto() = prepareBoundedPhoto(PhotoTarget.RESTORATION, ImageProcessor::fitForPhotoRestoration)

    private fun prepareBoundedPhoto(target: PhotoTarget, fit: (Bitmap) -> Bitmap) {
        val currentState = _state.value
        val targetPhoto = when (target) {
            PhotoTarget.BACKGROUND -> currentState.backgroundSource
            PhotoTarget.UPSCALER -> currentState.upscalerSource
            PhotoTarget.OBJECT -> currentState.objectSource
            PhotoTarget.RESTORATION -> currentState.restorationSource
            PhotoTarget.GENERAL -> return
        }
        if (targetPhoto != null || currentState.busy) return
        val source = currentState.source ?: return
        if (source.isRecycled) return
        _state.update { it.copy(busy = true, status = "Preparing photo for editing…") }
        viewModelScope.launch {
            val fitted = runCatching {
                withContext(Dispatchers.IO) {
                    val bounded = fit(source)
                    if (bounded !== source) bounded else source.copy(Bitmap.Config.ARGB_8888, false)
                        ?: throw IllegalStateException("Couldn't prepare this photo")
                }
            }.getOrNull()
            if (fitted == null) {
                val message = "Couldn't prepare this photo for safe editing. Choose a smaller photo."
                _state.update { state -> when (target) {
                    PhotoTarget.BACKGROUND -> state.copy(busy = false, status = message)
                    PhotoTarget.UPSCALER -> state.copy(busy = false, status = null, upscalerError = message)
                    PhotoTarget.OBJECT -> state.copy(busy = false, status = null, objectError = message)
                    PhotoTarget.RESTORATION -> state.copy(busy = false, status = null, restorationError = message)
                    PhotoTarget.GENERAL -> state
                } }
            } else {
                val downscaled = fitted.width < source.width || fitted.height < source.height
                _state.update { state ->
                    if (state.source !== source) return@update state
                    when (target) {
                        PhotoTarget.BACKGROUND -> if (state.backgroundSource == null) state.copy(backgroundSource = fitted, backgroundPhotoDownscaled = downscaled, busy = false, status = null) else state
                        PhotoTarget.UPSCALER -> if (state.upscalerSource == null) state.copy(upscalerSource = fitted, upscalerPhotoDownscaled = downscaled, busy = false, status = null) else state
                        PhotoTarget.OBJECT -> if (state.objectSource == null) state.copy(objectSource = fitted, objectPhotoDownscaled = downscaled, busy = false, status = null) else state
                        PhotoTarget.RESTORATION -> if (state.restorationSource == null) state.copy(restorationSource = fitted, restorationPhotoDownscaled = downscaled, busy = false, status = null) else state
                        PhotoTarget.GENERAL -> state
                    }
                }
                val wasAdopted = when (target) {
                    PhotoTarget.BACKGROUND -> _state.value.backgroundSource === fitted
                    PhotoTarget.UPSCALER -> _state.value.upscalerSource === fitted
                    PhotoTarget.OBJECT -> _state.value.objectSource === fitted
                    PhotoTarget.RESTORATION -> _state.value.restorationSource === fitted
                    PhotoTarget.GENERAL -> false
                }
                if (!wasAdopted && !fitted.isRecycled) fitted.recycle()
            }
        }
    }

    private fun loadPhoto(uri: Uri, target: PhotoTarget) {
        viewModelScope.launch {
            val currentState = _state.value
            val oldPhoto = when (target) {
                PhotoTarget.GENERAL -> currentState.source
                PhotoTarget.BACKGROUND -> currentState.backgroundSource
                PhotoTarget.UPSCALER -> currentState.upscalerSource
                PhotoTarget.OBJECT -> currentState.objectSource
                PhotoTarget.RESTORATION -> currentState.restorationSource
            }
            val oldResults = when (target) {
                PhotoTarget.GENERAL -> listOfNotNull(currentState.result, currentState.upscaleResult, currentState.objectResult).distinct()
                PhotoTarget.BACKGROUND -> listOfNotNull(currentState.result)
                PhotoTarget.UPSCALER -> listOfNotNull(currentState.upscaleResult)
                PhotoTarget.OBJECT -> listOfNotNull(currentState.objectResult)
                PhotoTarget.RESTORATION -> listOfNotNull(currentState.restorationResult, currentState.restorationMaskPreview).distinct()
            }
            _state.value = when (target) {
                PhotoTarget.GENERAL -> currentState.copy(busy = true, status = null, result = null, upscaleResult = null, objectResult = null, objectError = null)
                PhotoTarget.BACKGROUND -> currentState.copy(busy = true, status = null, result = null)
                PhotoTarget.UPSCALER -> currentState.copy(busy = true, status = null, upscaleResult = null, upscalerError = null)
                PhotoTarget.OBJECT -> currentState.copy(busy = true, objectResult = null, objectError = null)
                PhotoTarget.RESTORATION -> currentState.copy(busy = true, restorationResult = null, restorationMaskPreview = null, restorationError = null)
            }
            oldResults.forEach { if (!it.isRecycled) it.recycle() }
            try {
                val (bitmap, downscaled) = withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val decoded = when (target) {
                        PhotoTarget.GENERAL -> ImageProcessor.decode(app, uri)
                        PhotoTarget.BACKGROUND -> ImageProcessor.decodeForBackgroundRemoval(app, uri)
                        PhotoTarget.UPSCALER -> ImageProcessor.decodeForUpscaling(app, uri)
                        PhotoTarget.OBJECT -> ImageProcessor.decodeForObjectRemoval(app, uri)
                        PhotoTarget.RESTORATION -> ImageProcessor.decodeForPhotoRestoration(app, uri)
                    }
                    decoded to (target != PhotoTarget.GENERAL && wasResized(decoded, uri))
                }
                val latest = _state.value
                _state.value = when (target) {
                    PhotoTarget.GENERAL -> latest.copy(source = bitmap, busy = false)
                    PhotoTarget.BACKGROUND -> latest.copy(backgroundSource = bitmap, backgroundPhotoDownscaled = downscaled, busy = false)
                    PhotoTarget.UPSCALER -> latest.copy(upscalerSource = bitmap, upscalerPhotoDownscaled = downscaled, busy = false)
                    PhotoTarget.OBJECT -> latest.copy(objectSource = bitmap, objectPhotoDownscaled = downscaled, busy = false)
                    PhotoTarget.RESTORATION -> latest.copy(restorationSource = bitmap, restorationPhotoDownscaled = downscaled, busy = false)
                }
                oldPhoto?.let { if (it !== bitmap && !it.isRecycled) it.recycle() }
            } catch (_: Throwable) {
                val message = "Couldn't open that photo. Choose a JPG, PNG or WebP image."
                _state.value = when (target) {
                    PhotoTarget.OBJECT -> _state.value.copy(busy = false, objectError = message)
                    PhotoTarget.RESTORATION -> _state.value.copy(busy = false, restorationError = message)
                    PhotoTarget.UPSCALER -> _state.value.copy(busy = false, upscalerError = message)
                    else -> _state.value.copy(busy = false, status = message)
                }
            }
        }
    }

    private fun wasResized(bitmap: Bitmap, uri: Uri): Boolean {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri).use { input -> android.graphics.BitmapFactory.decodeStream(input, null, options) }
            val orientation = getApplication<Application>().contentResolver.openInputStream(uri).use { input ->
                androidx.exifinterface.media.ExifInterface(input!!).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)
            }
            val swaps = orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 || orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 || orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE || orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSVERSE
            val width = if (swaps) options.outHeight else options.outWidth
            val height = if (swaps) options.outWidth else options.outHeight
            bitmap.width < width || bitmap.height < height
        }.getOrDefault(false)
    }

    fun removeBackground() {
        val bitmap = _state.value.backgroundSource ?: return
        if (_state.value.busy) return
        if (_state.value.installed) {
            process(bitmap)
        } else {
            processAfterDownload = true
            download()
        }
    }

    fun download() {
        if (_state.value.downloading || _state.value.installed) return
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>().build()
        work.enqueueUniqueWork("ormbg-model", ExistingWorkPolicy.REPLACE, request)
        _state.value = _state.value.copy(downloading = true, status = null, downloadProgress = 0)
        viewModelScope.launch(Dispatchers.IO) {
          try {
            while (true) {
                val info = work.getWorkInfoById(request.id).get() ?: break
                val done = info.progress.getLong(ModelDownloadWorker.KEY_DONE, 0)
                val total = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL, ModelManager.EXPECTED_BYTES)
                when (info.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        _state.value = _state.value.copy(installed = true, modelBytes = ModelManager.EXPECTED_BYTES, downloading = false, downloadProgress = 100)
                        if (processAfterDownload) {
                            processAfterDownload = false
                            _state.value.backgroundSource?.let { process(it) }
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        processAfterDownload = false
                        _state.value = _state.value.copy(downloading = false, status = info.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Download failed")
                    }
                    WorkInfo.State.CANCELLED -> { processAfterDownload = false; _state.value = _state.value.copy(downloading = false) }
                    else -> _state.value = _state.value.copy(downloadProgress = ((done * 100 / total.coerceAtLeast(1)).toInt()).coerceIn(0, 99))
                }
                if (info.state.isFinished) break
                delay(400)
            }
          } catch (e: Exception) {
            _state.value = _state.value.copy(downloading = false, status = e.message ?: "Could not check download status")
          }
        }
    }
    fun downloadUpscaler() {
        if (_state.value.restorationDownloading) return
        if (_state.value.upscalerDownloading || _state.value.upscalerInstalled) return
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>().setInputData(androidx.work.workDataOf(ModelDownloadWorker.KEY_MODEL_ID to ModelSpecs.imageUpscaler.id)).build()
        work.enqueueUniqueWork("model:${ModelSpecs.imageUpscaler.id}", ExistingWorkPolicy.REPLACE, request)
        _state.value = _state.value.copy(upscalerDownloading = true, status = null, upscalerError = null, upscalerProgress = 0)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    val info = work.getWorkInfoById(request.id).get() ?: break
                    val done = info.progress.getLong(ModelDownloadWorker.KEY_DONE, 0)
                    val total = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL, ModelSpecs.imageUpscaler.downloadSizeBytes)
                    when (info.state) {
                        WorkInfo.State.SUCCEEDED -> _state.value = _state.value.copy(upscalerInstalled = true, upscalerBytes = upscalerManager.sizeBytes(), upscalerDownloading = false, upscalerProgress = 100)
                        WorkInfo.State.FAILED -> _state.value = _state.value.copy(upscalerDownloading = false, upscalerError = info.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Download failed", status = info.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Download failed")
                        WorkInfo.State.CANCELLED -> _state.value = _state.value.copy(upscalerDownloading = false)
                        else -> _state.value = _state.value.copy(upscalerProgress = ((done * 100 / total.coerceAtLeast(1)).toInt()).coerceIn(0, 99))
                    }
                    if (info.state.isFinished) break
                    delay(350)
                }
            } catch (e: Exception) { _state.value = _state.value.copy(upscalerDownloading = false, upscalerError = e.message ?: "Could not check download status", status = e.message ?: "Could not check download status") }
        }
    }
    fun cancelDownload() { processAfterDownload = false; work.cancelUniqueWork("ormbg-model"); _state.value = _state.value.copy(downloading = false) }
    fun cancelUpscalerDownload() { work.cancelUniqueWork("model:${ModelSpecs.imageUpscaler.id}"); _state.value = _state.value.copy(upscalerDownloading = false) }

    fun downloadSpeechModel() {
        if (_state.value.speechDownloading || _state.value.speechInstalled) return
        val spec = ModelSpecs.speechNoiseRemover
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(androidx.work.workDataOf(ModelDownloadWorker.KEY_MODEL_ID to spec.id)).build()
        work.enqueueUniqueWork("model:${spec.id}", ExistingWorkPolicy.REPLACE, request)
        _state.value = _state.value.copy(speechDownloading = true, speechProgress = 0, speechError = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    val info = work.getWorkInfoById(request.id).get() ?: break
                    val done = info.progress.getLong(ModelDownloadWorker.KEY_DONE, 0)
                    val total = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL, spec.downloadSizeBytes)
                    when (info.state) {
                        WorkInfo.State.SUCCEEDED -> _state.value = _state.value.copy(speechInstalled = true, speechBytes = speechManager.sizeBytes(), speechDownloading = false, speechProgress = 100)
                        WorkInfo.State.FAILED -> {
                            val error = info.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Download failed"
                            _state.value = _state.value.copy(speechDownloading = false, speechError = error, status = error)
                        }
                        WorkInfo.State.CANCELLED -> _state.value = _state.value.copy(speechDownloading = false)
                        else -> _state.value = _state.value.copy(speechProgress = ((done * 100 / total.coerceAtLeast(1)).toInt()).coerceIn(0, 99))
                    }
                    if (info.state.isFinished) break
                    delay(350)
                }
            } catch (e: Exception) {
                val error = e.message ?: "Could not check download status"
                _state.value = _state.value.copy(speechDownloading = false, speechError = error)
            }
        }
    }

    fun cancelSpeechDownload() { work.cancelUniqueWork("model:${ModelSpecs.speechNoiseRemover.id}"); _state.value = _state.value.copy(speechDownloading = false) }

    fun downloadTranscriptionModel() {
        if (_state.value.transcriptionDownloading || _state.value.transcriptionInstalled) return
        val spec = ModelSpecs.speechToText
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(androidx.work.workDataOf(ModelDownloadWorker.KEY_MODEL_ID to spec.id)).build()
        work.enqueueUniqueWork("model:${spec.id}", ExistingWorkPolicy.REPLACE, request)
        _state.value = _state.value.copy(transcriptionDownloading = true, transcriptionProgress = 0, transcriptionError = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    val info = work.getWorkInfoById(request.id).get() ?: break
                    val done = info.progress.getLong(ModelDownloadWorker.KEY_DONE, 0)
                    val total = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL, spec.downloadSizeBytes)
                    when (info.state) {
                        WorkInfo.State.SUCCEEDED -> _state.value = _state.value.copy(transcriptionInstalled = true, transcriptionBytes = transcriptionManager.sizeBytes(), transcriptionDownloading = false, transcriptionProgress = 100)
                        WorkInfo.State.FAILED -> _state.value = _state.value.copy(transcriptionDownloading = false, transcriptionError = info.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Download failed")
                        WorkInfo.State.CANCELLED -> _state.value = _state.value.copy(transcriptionDownloading = false)
                        else -> _state.value = _state.value.copy(transcriptionProgress = ((done * 100 / total.coerceAtLeast(1)).toInt()).coerceIn(0, 99))
                    }
                    if (info.state.isFinished) break
                    delay(350)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(transcriptionDownloading = false, transcriptionError = e.message ?: "Could not check download status")
            }
        }
    }

    fun cancelTranscriptionDownload() {
        work.cancelUniqueWork("model:${ModelSpecs.speechToText.id}")
        _state.value = _state.value.copy(transcriptionDownloading = false)
    }

    fun chooseTranscriptionAudio(uri: Uri) {
        val name = getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
        stopAudio()
        _state.value = _state.value.copy(transcriptionSource = uri, transcriptionName = name ?: "Selected recording", transcriptText = "", transcriptLanguage = null, transcriptMarks = emptyList(), transcriptionError = null, status = null)
    }

    fun startTranscriptionRecording() {
        if (_state.value.transcriptionRecording) return
        var activeRecorder: MediaRecorder? = null
        try {
            val file = getApplication<Application>().cacheDir.resolve("recording-${System.currentTimeMillis()}.m4a")
            @Suppress("DEPRECATION")
            val recorder = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(getApplication<Application>()) else MediaRecorder()
            activeRecorder = recorder
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioChannels(1)
            recorder.setAudioSamplingRate(16_000)
            recorder.setAudioEncodingBitRate(64_000)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            recordingFile = file
            mediaRecorder = recorder
            _state.value = _state.value.copy(transcriptionRecording = true, transcriptionError = null, transcriptText = "", transcriptMarks = emptyList())
        } catch (_: Exception) {
            runCatching { activeRecorder?.release() }
            mediaRecorder = null
            _state.value = _state.value.copy(transcriptionRecording = false, transcriptionError = "Couldn't start recording. Check microphone access and available storage.")
        }
    }

    fun stopTranscriptionRecording() {
        val recorder = mediaRecorder ?: return
        val file = recordingFile
        val stopped = runCatching { recorder.stop() }.isSuccess
        runCatching { recorder.reset() }
        runCatching { recorder.release() }
        mediaRecorder = null
        recordingFile = null
        if (stopped && file?.isFile == true && file.length() > 0L) {
            _state.value = _state.value.copy(transcriptionRecording = false, transcriptionSource = Uri.fromFile(file), transcriptionName = "Voice recording.m4a", transcriptionError = null)
        } else {
            file?.delete()
            _state.value = _state.value.copy(transcriptionRecording = false, transcriptionError = "The recording was too short. Try recording again.")
        }
    }

    fun transcribeAudio() {
        val source = _state.value.transcriptionSource ?: return
        if (_state.value.transcriptionBusy || !_state.value.transcriptionInstalled) return
        transcriptionJob = viewModelScope.launch {
            val currentJob = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
            _state.value = _state.value.copy(transcriptionBusy = true, transcriptionProgress = 0, transcriptionStage = "Preparing audio…", transcriptionError = null)
            try {
                val samples = withContext(Dispatchers.IO) {
                    WavAudio.resampleTo16k(WavAudio.readMono48k(getApplication<Application>(), source))
                }
                _state.value = _state.value.copy(transcriptionStage = "Transcribing on your phone…")
                val selectedLanguage = _state.value.transcriptionLanguageSelection
                val sherpaLanguage = selectedLanguage.takeIf { it != WhisperLanguages.AUTO && WhisperLanguages.supported.any { language -> language.code == it } }.orEmpty()
                val result = withContext(Dispatchers.Default) {
                    val engine = transcriptionEngine?.takeIf { transcriptionEngineLanguage == sherpaLanguage }
                        ?: run {
                            transcriptionEngine?.close()
                            WhisperSpeechToText(java.io.File(transcriptionManager.path()), sherpaLanguage).also {
                                transcriptionEngine = it
                                transcriptionEngineLanguage = sherpaLanguage
                            }
                        }
                    engine.transcribe(
                        samples,
                        shouldCancel = { currentJob?.isActive == false },
                        onProgress = { done, total ->
                            _state.value = _state.value.copy(transcriptionProgress = (done * 100 / total.coerceAtLeast(1)).coerceIn(0, 99))
                        },
                    )
                }
                _state.value = _state.value.copy(transcriptText = result.text, transcriptLanguage = result.language.takeIf { it.isNotBlank() }, transcriptMarks = result.timeMarks, transcriptionBusy = false, transcriptionProgress = 100, transcriptionStage = null)
                if (result.text.isBlank()) _state.value = _state.value.copy(transcriptionError = "No speech was found. Try a recording with clearer speech.")
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = _state.value.copy(transcriptionBusy = false, transcriptionStage = null)
                throw e
            } catch (e: Throwable) {
                val message = e.message.orEmpty()
                _state.value = _state.value.copy(transcriptionBusy = false, transcriptionStage = null, transcriptionError = when {
                    isAudioLengthLimit(message) -> "This recording is longer than this tool can handle. Try a shorter recording (under five minutes)."
                    message.contains("isn't supported", true) || message.contains("is not supported", true) -> message
                    message.contains("empty", true) -> "This recording is empty. Choose another file."
                    isDeviceLimitation(e) -> "This phone couldn't process the recording. Try a shorter clip or close other apps, then try again."
                    else -> "Couldn't transcribe this recording. Try another file or a shorter clip."
                })
            }
        }
    }

    fun cancelTranscription() { transcriptionJob?.cancel(); transcriptionJob = null }
    fun setTranscriptionLanguage(value: String) {
        if (value != WhisperLanguages.AUTO && WhisperLanguages.supported.none { it.code == value }) return
        preferences.edit().putString("transcription_language", value).apply()
        _state.value = _state.value.copy(transcriptionLanguageSelection = value)
    }
    fun updateTranscript(value: String) { _state.value = _state.value.copy(transcriptText = value, status = null) }
    fun setTranscriptionError(message: String) { _state.value = _state.value.copy(transcriptionError = message) }

    fun playTranscriptionAudio() {
        val source = _state.value.transcriptionSource ?: return
        if (_state.value.audioPlaying == "transcription") { stopAudio(); return }
        stopAudio()
        val player = MediaPlayer()
        mediaPlayer = player
        try {
            player.setDataSource(getApplication<Application>(), source)
            player.setOnPreparedListener { it.start(); _state.value = _state.value.copy(audioPlaying = "transcription") }
            player.setOnCompletionListener { stopAudio() }
            player.setOnErrorListener { _, _, _ -> stopAudio(); _state.value = _state.value.copy(transcriptionError = "Could not play this recording."); true }
            player.prepareAsync()
        } catch (_: Exception) { stopAudio(); _state.value = _state.value.copy(transcriptionError = "Couldn't play this recording.") }
    }

    fun saveTranscript(destination: Uri) {
        val text = _state.value.transcriptText
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { getApplication<Application>().contentResolver.openOutputStream(destination)?.bufferedWriter()?.use { it.write(text) } ?: error("Couldn't create the text file") }
                .onSuccess { _state.value = _state.value.copy(status = "Transcript saved", transcriptionError = null) }
                .onFailure { _state.value = _state.value.copy(transcriptionError = "Couldn't save the transcript. Please try again.") }
        }
    }

    fun deleteTranscriptionModel() {
        val engine = transcriptionEngine
        transcriptionEngine = null
        transcriptionEngineLanguage = null
        _state.value = _state.value.copy(transcriptionInstalled = false, transcriptionBytes = 0L, status = "Speech-to-Text model removed")
        viewModelScope.launch(Dispatchers.IO) { runCatching { engine?.close() }; transcriptionManager.delete() }
    }

    fun downloadObjectModel() {
        val spec = ModelSpecs.objectRemover
        if (_state.value.objectDownloading || _state.value.objectInstalled) return
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(androidx.work.workDataOf(ModelDownloadWorker.KEY_MODEL_ID to spec.id)).build()
        work.enqueueUniqueWork("model:${spec.id}", ExistingWorkPolicy.REPLACE, request)
        _state.value = _state.value.copy(objectDownloading = true, objectProgress = -1, objectError = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    val info = work.getWorkInfoById(request.id).get() ?: break
                    val done = info.progress.getLong(ModelDownloadWorker.KEY_DONE, 0)
                    val total = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL, spec.downloadSizeBytes)
                    when (info.state) {
                        WorkInfo.State.SUCCEEDED -> _state.value = _state.value.copy(objectInstalled = true, objectBytes = objectManager.sizeBytes(), objectDownloading = false, objectProgress = 100)
                        WorkInfo.State.FAILED -> _state.value = _state.value.copy(objectDownloading = false, objectError = info.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Download failed")
                        WorkInfo.State.CANCELLED -> _state.value = _state.value.copy(objectDownloading = false)
                        else -> _state.value = _state.value.copy(objectProgress = if (total > 0L) ((done * 100 / total).toInt()).coerceIn(0, 99) else -1)
                    }
                    if (info.state.isFinished) break
                    delay(350)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(objectDownloading = false, objectError = e.message ?: "Couldn't check the download")
            }
        }
    }

    fun cancelObjectDownload() { work.cancelUniqueWork("model:${ModelSpecs.objectRemover.id}"); _state.value = _state.value.copy(objectDownloading = false) }

    fun removeObjects(mask: Bitmap) {
        val bitmap = _state.value.objectSource ?: return
        if (_state.value.objectProcessing || !_state.value.objectInstalled) return
        viewModelScope.launch {
            _state.value.objectResult?.let { if (!it.isRecycled) it.recycle() }
            _state.value = _state.value.copy(objectProcessing = true, objectError = null, objectResult = null, objectTileProgress = 0, objectTileTotal = 0)
            try {
                val result = withContext(Dispatchers.IO) {
                    val maxPixels = (Runtime.getRuntime().maxMemory() * .35 / 16).toLong()
                    require(bitmap.width.toLong() * bitmap.height <= maxPixels) { "This photo is too large to edit safely on this phone. Choose a smaller photo." }
                    val engine = objectRemover ?: LamaObjectRemover.fromFile(objectManager.path()).also { objectRemover = it }
                    engine.process(bitmap, mask) { done, total -> _state.value = _state.value.copy(objectTileProgress = done, objectTileTotal = total) }
                }
                _state.value = _state.value.copy(objectProcessing = false, objectResult = result)
            } catch (e: Throwable) {
                val message = e.message.orEmpty()
                _state.value = _state.value.copy(objectProcessing = false, objectError = when {
                    message.contains("cancel", true) -> "Object removal cancelled."
                    message.contains("large", true) || message.contains("memory", true) -> "This photo is too large for Object Remover on this phone. Try a smaller photo or close other apps, then try again."
                    message.contains("paint", true) -> message
                    isDeviceLimitation(e) -> "This phone couldn't run Object Remover with the available memory or graphics support. Try a smaller photo or close other apps, then try again."
                    else -> "Couldn't remove that object on this phone. Try a smaller photo or a different selection."
                })
            } finally { if (!mask.isRecycled) mask.recycle() }
        }
    }

    fun cancelObjectProcessing() { objectRemover?.cancel() }

    fun resetObjectResult() {
        val result = _state.value.objectResult
        _state.value = _state.value.copy(objectResult = null, objectError = null, objectTileProgress = 0, objectTileTotal = 0)
        if (result != null && !result.isRecycled) result.recycle()
    }

    fun downloadRestorationModels() {
        if (_state.value.restorationDownloading) return
        if (_state.value.restorationScratchInstalled && _state.value.restorationLamaInstalled && _state.value.restorationColorizerInstalled && _state.value.restorationEnhancerInstalled) return
        restorationDownloadJob?.cancel()
        _state.value = _state.value.copy(restorationDownloading = true, restorationProgress = 0, restorationError = null)
        restorationDownloadJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val scratchSize = ModelSpecs.restorationScratch.downloadSizeBytes
                    val lamaSize = ModelSpecs.restorationLama.downloadSizeBytes
                    val colorizerSize = ModelSpecs.restorationColorizer.downloadSizeBytes
                    val enhancerSize = ModelSpecs.restorationEnhancer.downloadSizeBytes
                    val needScratch = !restorationStore.scratchInstalled()
                    val needLama = !restorationStore.lamaInstalled()
                    val needColorizer = !restorationStore.colorizerInstalled()
                    val needEnhancer = !restorationStore.enhancerInstalled()
                    val totalSize = (if (needScratch) scratchSize else 0L) + (if (needLama) lamaSize else 0L) + (if (needColorizer) colorizerSize else 0L) + (if (needEnhancer) enhancerSize else 0L)
                    var base = 0L
                    if (needScratch) {
                        restorationStore.downloadScratch { done, _ ->
                            val progress = ((base + done) * 100 / totalSize).toInt().coerceIn(0, 99)
                            _state.value = _state.value.copy(restorationProgress = progress)
                        }
                        base += scratchSize
                    }
                    if (needLama) {
                        restorationStore.downloadLama { done, _ ->
                            val progress = ((base + done) * 100 / totalSize).toInt().coerceIn(0, 99)
                            _state.value = _state.value.copy(restorationProgress = progress)
                        }
                        base += lamaSize
                    }
                    if (needColorizer) {
                        restorationStore.downloadColorizer { done, _ ->
                            val progress = ((base + done) * 100 / totalSize).toInt().coerceIn(0, 99)
                            _state.value = _state.value.copy(restorationProgress = progress)
                        }
                        base += colorizerSize
                    }
                    if (needEnhancer) {
                        restorationStore.downloadEnhancer { done, _ ->
                            val progress = ((base + done) * 100 / totalSize).toInt().coerceIn(0, 99)
                            _state.value = _state.value.copy(restorationProgress = progress)
                        }
                        base += enhancerSize
                    }
                }
                _state.value = _state.value.copy(
                    restorationDownloading = false,
                    restorationProgress = 100,
                    restorationScratchInstalled = true,
                    restorationLamaInstalled = true,
                    restorationColorizerInstalled = true,
                    restorationEnhancerInstalled = true,
                    restorationBytes = restorationStore.scratchBytes() + restorationStore.lamaBytes() + restorationStore.colorizerBytes() + restorationStore.enhancerBytes(),
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = _state.value.copy(restorationDownloading = false, restorationEnhancerInstalled = restorationStore.enhancerInstalled(), restorationBytes = restorationStore.scratchBytes() + restorationStore.lamaBytes() + restorationStore.colorizerBytes() + restorationStore.enhancerBytes())
                throw e
            } catch (e: Throwable) {
                _state.value = _state.value.copy(restorationDownloading = false, restorationEnhancerInstalled = restorationStore.enhancerInstalled(), restorationBytes = restorationStore.scratchBytes() + restorationStore.lamaBytes() + restorationStore.colorizerBytes() + restorationStore.enhancerBytes(), restorationError = e.message ?: "Couldn't download the restoration models. Please retry.")
            }
        }
    }

    fun cancelRestorationDownload() {
        restorationDownloadJob?.cancel()
        restorationDownloadJob = null
        _state.value = _state.value.copy(restorationDownloading = false)
    }

    fun restorePhoto() {
        val source = _state.value.restorationSource ?: return
        if (_state.value.restorationProcessing) return
        if (!_state.value.restorationScratchInstalled || !_state.value.restorationLamaInstalled || !_state.value.restorationColorizerInstalled || !_state.value.restorationEnhancerInstalled) {
            downloadRestorationModels()
            return
        }
        viewModelScope.launch {
            _state.value.restorationResult?.let { if (!it.isRecycled) it.recycle() }
            _state.value.restorationMaskPreview?.let { if (!it.isRecycled) it.recycle() }
            _state.value = _state.value.copy(restorationProcessing = true, restorationError = null, restorationStage = "Preparing photo", restorationTileProgress = 0, restorationTileTotal = 0)
            try {
                val result = withContext(Dispatchers.IO) {
                    val engine = restorationPipeline ?: PhotoRestorationPipeline(
                        restorationStore.scratchPath(), restorationStore.lamaPath(),
                        restorationStore.colorizerPath(), restorationStore.enhancerPath(),
                    ).also { restorationPipeline = it }
                    engine.restore(source) { stage, done, total ->
                        _state.value = _state.value.copy(restorationStage = stage, restorationTileProgress = done, restorationTileTotal = total)
                    }
                }
                _state.value = _state.value.copy(
                    restorationProcessing = false,
                    restorationStage = null,
                    restorationMaskPreview = result.maskPreview,
                    restorationResult = result.restored,
                    restorationTileProgress = result.tilesProcessed,
                    restorationTileTotal = result.tilesProcessed,
                )
            } catch (e: Throwable) {
                val message = e.message.orEmpty()
                _state.value = _state.value.copy(
                    restorationProcessing = false,
                    restorationStage = null,
                    restorationError = when {
                        message.contains("cancel", true) -> "Restoration cancelled."
                        message.contains("large", true) || message.contains("memory", true) -> "This photo is too large to restore on this phone. Try a smaller photo or close other apps, then retry."
                        isDeviceLimitation(e) -> "This phone couldn't run the restoration models with the available memory. Try a smaller photo or close other apps, then retry."
                        else -> "Couldn't restore this photo. Try another image or a smaller photo."
                    },
                )
            }
        }
    }

    fun cancelRestoration() { restorationPipeline?.cancel() }

    fun saveRestoredPhoto() {
        val bitmap = _state.value.restorationResult ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ImageExporter.save(getApplication(), bitmap, "dapple-restored-photo")
                _state.value = _state.value.copy(restorationError = "Restored photo saved to Pictures/Dapple")
            } catch (_: Throwable) {
                _state.value = _state.value.copy(restorationError = "Couldn't save the restored photo. Please try again.")
            }
        }
    }

    fun deleteRestorationModels() {
        cancelRestorationDownload()
        val engine = restorationPipeline
        restorationPipeline = null
        _state.value = _state.value.copy(restorationScratchInstalled = false, restorationLamaInstalled = false, restorationColorizerInstalled = false, restorationEnhancerInstalled = false, restorationBytes = 0L, restorationError = null)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { engine?.close() }
            restorationStore.deleteScratch()
            restorationStore.deleteLama()
            restorationStore.deleteColorizer()
            restorationStore.deleteEnhancer()
        }
    }

    fun chooseSpeechAudio(uri: Uri) {
        val name = getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
        _state.value = _state.value.copy(audioSource = uri, audioName = name ?: "Selected audio file", enhancedAudioPath = null, speechError = null, audioPlaying = null)
        stopAudio()
    }

    fun removeSpeechNoise() {
        val source = _state.value.audioSource ?: return
        if (_state.value.speechBusy || !_state.value.speechInstalled) return
        viewModelScope.launch {
            _state.value = _state.value.copy(speechBusy = true, speechError = null, enhancedAudioPath = null, speechTileProgress = 0)
            try {
                val output = withContext(Dispatchers.IO) {
                    val samples = WavAudio.readMono48k(getApplication<Application>(), source)
                    val engine = speechDenoiser ?: DpdfnetSpeechDenoiser.fromFile(speechManager.path()).also { speechDenoiser = it }
                    val enhanced = engine.enhance(samples) { done, total -> _state.value = _state.value.copy(speechTileProgress = done, speechTileTotal = total) }
                    val file = getApplication<Application>().cacheDir.resolve("speech-enhanced-${System.currentTimeMillis()}.wav")
                    WavAudio.writeMono48k(file, enhanced)
                    file
                }
                _state.value = _state.value.copy(speechBusy = false, enhancedAudioPath = output.absolutePath)
            } catch (e: Throwable) {
                val message = e.message.orEmpty()
                val friendlyMessage = when {
                    isAudioLengthLimit(message) -> "This recording is longer than this tool can handle. Try a shorter recording (under five minutes)."
                    message.contains("isn't supported", ignoreCase = true) || message.contains("is not supported", ignoreCase = true) || message.contains("Try a WAV", ignoreCase = true) -> message
                    isDeviceLimitation(e) -> "This phone couldn't clean this recording. Try a shorter clip or close other apps, then try again."
                    else -> "Couldn't clean up this recording. Try another audio file."
                }
                _state.value = _state.value.copy(speechBusy = false, speechError = friendlyMessage)
            }
        }
    }

    fun playAudio(source: String) {
        val state = _state.value
        if (state.audioPlaying == source) { stopAudio(); return }
        stopAudio()
        val player = MediaPlayer()
        mediaPlayer = player
        try {
            if (source == "original") player.setDataSource(getApplication<Application>(), state.audioSource!!)
            else player.setDataSource(state.enhancedAudioPath!!)
            player.setOnPreparedListener { it.start(); _state.value = _state.value.copy(audioPlaying = source) }
            player.setOnCompletionListener { stopAudio() }
            player.setOnErrorListener { _, _, _ -> stopAudio(); _state.value = _state.value.copy(speechError = "Could not play this audio file"); true }
            player.prepareAsync()
        } catch (_: Exception) {
            stopAudio(); _state.value = _state.value.copy(speechError = "Couldn't play this recording. Try another audio file.")
        }
    }

    fun stopAudio() {
        runCatching { mediaPlayer?.stop() }; runCatching { mediaPlayer?.release() }; mediaPlayer = null
        _state.value = _state.value.copy(audioPlaying = null)
    }

    fun cancelSpeechProcessing() { speechDenoiser?.cancel() }

    fun saveEnhancedAudio(destination: Uri) {
        val source = _state.value.enhancedAudioPath ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.openOutputStream(destination)?.use { output ->
                    java.io.File(source).inputStream().buffered().use { it.copyTo(output) }
                } ?: throw IllegalStateException("Could not create the WAV file")
                _state.value = _state.value.copy(status = "Enhanced WAV saved", speechError = null)
            } catch (_: Exception) { _state.value = _state.value.copy(speechError = "Couldn't save the cleaned recording. Please try again.") }
        }
    }

    fun deleteSpeechModel() {
        val engine = speechDenoiser; speechDenoiser = null
        _state.value = _state.value.copy(speechInstalled = false, speechBytes = 0, status = "Speech Noise Remover removed")
        viewModelScope.launch(Dispatchers.IO) { runCatching { engine?.close() }; speechManager.delete() }
    }

    fun deleteObjectModel() {
        val engine = objectRemover; objectRemover = null
        _state.value = _state.value.copy(objectInstalled = false, objectBytes = 0, objectError = null, status = "Object Remover removed")
        viewModelScope.launch(Dispatchers.IO) { runCatching { engine?.close() }; objectManager.delete() }
    }

    fun upscale() {
        val bitmap = _state.value.upscalerSource ?: return
        if (_state.value.busy) return
        if (!_state.value.upscalerInstalled) { downloadUpscaler(); return }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, status = "Getting your photo ready…", upscaleResult = null, upscalerTileProgress = 0)
            try {
                val result = withContext(Dispatchers.IO) {
                    val engine = upscaler ?: RealEsrganUpscaler.fromFile(upscalerManager.path()).also { upscaler = it }
                    _state.value = _state.value.copy(status = "Enhancing your photo…", upscalerBackend = engine.backend)
                    engine.process(bitmap) { done, total -> _state.value = _state.value.copy(upscalerTileProgress = done, upscalerTileTotal = total) }
                }
                _state.value = _state.value.copy(upscaleResult = result, busy = false, status = null)
            } catch (e: Throwable) {
                val message = e.message.orEmpty()
                val friendlyMessage = when {
                    isDeviceLimitation(e) -> "This phone couldn't enhance this photo with the available memory or graphics support. Try a smaller photo or close other apps, then try again."
                    message.contains("cancel", ignoreCase = true) -> "Enhancement cancelled."
                    else -> "Couldn't enhance this photo. Please try again."
                }
                _state.value = _state.value.copy(busy = false, status = friendlyMessage)
            }
        }
    }

    fun process() { _state.value.backgroundSource?.let(::process) }
    fun process(bitmap: Bitmap) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, status = "Getting your photo ready…", result = null)
            try {
                val result = withContext(Dispatchers.IO) {
                    val engine = remover ?: BgRemover.fromFile(getApplication(), manager.path()).also { remover = it }
                    _state.value = _state.value.copy(status = "Removing the background…")
                    engine.process(bitmap)?.cutout(bitmap) ?: return@withContext null
                }
                _state.value = _state.value.copy(result = result, busy = false, status = if (result == null) "Processing cancelled" else null)
            } catch (e: Throwable) {
                val message = e.message.orEmpty()
                val friendlyMessage = if (isDeviceLimitation(e)) {
                    "This phone couldn't run Background Remover with the available memory or graphics support. Try a smaller photo or close other apps, then try again."
                } else {
                    "Background Remover couldn't process this photo. Try another photo."
                }
                _state.value = _state.value.copy(busy = false, status = friendlyMessage)
            }
        }
    }
    fun cancelProcessing() { remover?.cancel(); upscaler?.cancel(); _state.value = _state.value.copy(busy = false, status = "Cancelling…") }
    fun setSaved(uri: Uri) { _state.value = _state.value.copy(savedUri = uri, status = "Saved to your photo library") }
    fun reportError(message: String) { _state.value = _state.value.copy(status = message) }

    private fun isAudioLengthLimit(message: String): Boolean =
        message.contains("five minutes", true) ||
            message.contains("too long to process", true) ||
            message.contains("audio file is too large", true)

    /** Recognizes resource/runtime failures from on-device inference without recasting ordinary input errors. */
    private fun isDeviceLimitation(error: Throwable): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            val message = cause.message.orEmpty()
            if (cause is OutOfMemoryError ||
                message.contains("memory", true) || message.contains("out of memory", true) ||
                message.contains("couldn't start on this phone", true) ||
                message.contains("could not initialize", true) || message.contains("couldn't initialize", true) ||
                message.contains("accelerator", true) || message.contains("delegate", true) ||
                message.contains("GPU", true) || message.contains("NPU", true) ||
                message.contains("unsupported operation", true) || message.contains("not supported on this device", true)
            ) return true
            cause = cause.cause
        }
        return false
    }

    fun showSettings(show: Boolean) { _state.value = _state.value.copy(settings = show) }
    fun deleteModel() {
        val engine = remover
        remover = null
        _state.value = _state.value.copy(installed = false, modelBytes = 0, settings = false, status = "Background Remover removed")
        viewModelScope.launch(Dispatchers.IO) { manager.delete(); runCatching { engine?.close() } }
    }
    fun deleteUpscalerModel() {
        val engine = upscaler; upscaler = null
        _state.value = _state.value.copy(upscalerInstalled = false, upscalerBytes = 0, status = "Photo Enhancer removed")
        viewModelScope.launch(Dispatchers.IO) { engine?.cancel(); runCatching { engine?.close() }; upscalerManager.delete() }
    }
    fun setThemePreference(value: String) {
        preferences.edit().putString("theme", value).apply()
        _state.value = _state.value.copy(themePreference = value)
    }
    override fun onCleared() { remover?.cancel(); upscaler?.cancel(); speechDenoiser?.cancel(); objectRemover?.cancel(); restorationPipeline?.cancel(); restorationDownloadJob?.cancel(); documentDownloadJob?.cancel(); cancelTranscription(); runCatching { mediaRecorder?.stop() }; runCatching { mediaRecorder?.release() }; mediaRecorder = null; stopAudio(); viewModelScope.launch(Dispatchers.IO) { runCatching { remover?.close() }; runCatching { upscaler?.close() }; runCatching { speechDenoiser?.close() }; runCatching { objectRemover?.close() }; runCatching { restorationPipeline?.close() }; runCatching { transcriptionEngine?.close() }; runCatching { documentOcrEngine?.close() } }; super.onCleared() }
}
