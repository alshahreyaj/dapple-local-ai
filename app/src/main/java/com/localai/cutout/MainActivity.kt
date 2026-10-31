package com.localai.cutout

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localai.cutout.catalog.AiFeature
import com.localai.cutout.catalog.FeatureCatalog
import com.localai.cutout.ui.AppViewModel
import com.localai.cutout.ui.DetailsScreen
import com.localai.cutout.ui.DownloadsScreen
import com.localai.cutout.ui.ExploreScreen
import com.localai.cutout.ui.LocoraSettingsScreen
import com.localai.cutout.ui.LicenseNoticesScreen
import com.localai.cutout.ui.PrivacyPolicyScreen
import com.localai.cutout.ui.ToolScreen
import com.localai.cutout.ui.UpscalerToolScreen
import com.localai.cutout.ui.SpeechNoiseRemoverScreen
import com.localai.cutout.ui.ObjectRemoverScreen
import com.localai.cutout.ui.SpeechToTextScreen

private enum class Destination(val title: String) { EXPLORE("Explore"), DOWNLOADS("Downloads"), SETTINGS("Settings") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LocoraApp() }
    }
}

@Composable
private fun LocoraApp(vm: AppViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var destination by remember { mutableStateOf(Destination.EXPLORE) }
    var selectedFeature by remember { mutableStateOf<AiFeature?>(null) }
    var toolOpen by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showLicenseNotices by remember { mutableStateOf(false) }
    val exploreListState = rememberLazyListState()
    val isDark = when (state.themePreference) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            when (selectedFeature?.id) {
                "object-remover" -> vm.chooseObjectPhoto(it)
                "image-upscaler" -> vm.chooseUpscalerPhoto(it)
                FeatureCatalog.backgroundRemover.id -> vm.chooseBackgroundPhoto(it)
                else -> vm.choose(it)
            }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { if (selectedFeature?.id == "speech-to-text") vm.chooseTranscriptionAudio(it) else vm.chooseSpeechAudio(it) } }
    val wavSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri -> uri?.let(vm::saveEnhancedAudio) }
    val transcriptSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> uri?.let(vm::saveTranscript) }
    val recordPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startTranscriptionRecording() else vm.setTranscriptionError("Microphone access is needed to record audio. You can still choose an audio file.")
    }
    BackHandler(enabled = showPrivacyPolicy || showLicenseNotices || toolOpen || selectedFeature != null || destination != Destination.EXPLORE) {
        when {
            showPrivacyPolicy -> showPrivacyPolicy = false
            showLicenseNotices -> showLicenseNotices = false
            toolOpen -> {
                if (selectedFeature?.id == "noise-remover" || selectedFeature?.id == "speech-to-text") vm.stopAudio()
                if (selectedFeature?.id == "speech-to-text") {
                    vm.cancelTranscription()
                    if (state.transcriptionRecording) vm.stopTranscriptionRecording()
                }
                toolOpen = false
                selectedFeature = null
            }
            selectedFeature != null -> selectedFeature = null
            destination != Destination.EXPLORE -> destination = Destination.EXPLORE
        }
    }
    val colors = if (isDark) darkColorScheme(
        primary = Color(0xFFA3D8BC), onPrimary = Color(0xFF123D2D), background = Color(0xFF101612),
        surface = Color(0xFF1A211D), secondary = Color(0xFFBEE8D5), surfaceVariant = Color(0xFF27322C),
    ) else lightColorScheme(
        primary = Color(0xFF285A4C), onPrimary = Color.White, background = Color(0xFFF7F8F5),
        surface = Color.White, secondary = Color(0xFFBEE8D5), surfaceVariant = Color(0xFFEAF3ED),
    )

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!showPrivacyPolicy && !showLicenseNotices && selectedFeature == null && !toolOpen) NavigationBar {
                    Destination.entries.forEach { item ->
                        val icon = when (item) {
                            Destination.EXPLORE -> Icons.Rounded.Explore
                            Destination.DOWNLOADS -> Icons.Rounded.Storage
                            Destination.SETTINGS -> Icons.Rounded.Settings
                        }
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { destination = item },
                            icon = { androidx.compose.material3.Icon(icon, contentDescription = item.title) },
                            label = { androidx.compose.material3.Text(item.title) },
                        )
                    }
                }
            },
        ) { padding ->
            when {
                showPrivacyPolicy -> PrivacyPolicyScreen(
                    onBack = { showPrivacyPolicy = false },
                    modifier = Modifier.padding(padding),
                )
                showLicenseNotices -> LicenseNoticesScreen(
                    onBack = { showLicenseNotices = false },
                    modifier = Modifier.padding(padding),
                )
                toolOpen && selectedFeature?.id == "speech-to-text" -> SpeechToTextScreen(
                    state = state,
                    onBack = { if (state.transcriptionRecording) vm.stopTranscriptionRecording(); vm.stopAudio(); toolOpen = false; selectedFeature = null },
                    onChooseAudio = { audioPicker.launch(arrayOf("audio/*", "application/ogg")) },
                    onRecord = {
                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startTranscriptionRecording()
                        else recordPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onStopRecording = vm::stopTranscriptionRecording,
                    onPlayAudio = vm::playTranscriptionAudio,
                    onTranscribe = vm::transcribeAudio,
                    onCancel = vm::cancelTranscription,
                    onEditTranscript = vm::updateTranscript,
                    onSave = { transcriptSaver.launch("dapple-transcript.txt") },
                    onLanguageSelected = vm::setTranscriptionLanguage,
                    modifier = Modifier.padding(padding),
                )
                toolOpen && selectedFeature?.id == "noise-remover" -> SpeechNoiseRemoverScreen(
                    state = state,
                    onBack = { vm.stopAudio(); toolOpen = false; selectedFeature = null },
                    onChooseAudio = { audioPicker.launch(arrayOf("audio/*", "application/ogg")) },
                    onRemoveNoise = vm::removeSpeechNoise,
                    onPlayAudio = vm::playAudio,
                    onSaveWav = { wavSaver.launch("dapple-speech-noise-reduced.wav") },
                    onCancelProcessing = vm::cancelSpeechProcessing,
                    modifier = Modifier.padding(padding),
                )
                toolOpen && selectedFeature?.id == "image-upscaler" -> UpscalerToolScreen(
                    state = state,
                    onBack = { toolOpen = false; selectedFeature = null },
                    onChoosePhoto = { picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onUpscale = vm::upscale,
                    onCancel = { vm.cancelProcessing(); vm.cancelUpscalerDownload() },
                    onSaved = { vm.reportError("Saved to your photo library") },
                    onError = vm::reportError,
                    onPreparePhoto = vm::prepareUpscalerPhoto,
                    modifier = Modifier.padding(padding),
                )
                toolOpen && selectedFeature?.id == "object-remover" -> ObjectRemoverScreen(
                    state = state,
                    onBack = { toolOpen = false; selectedFeature = null },
                    onChoosePhoto = { picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onRemoveObjects = vm::removeObjects,
                    onCancel = { vm.cancelObjectProcessing(); vm.cancelObjectDownload() },
                    onResetResult = vm::resetObjectResult,
                    onSaved = { vm.reportError("Saved to your photo library") },
                    onError = vm::reportError,
                    onPreparePhoto = vm::prepareObjectPhoto,
                    modifier = Modifier.padding(padding),
                )
                toolOpen || (selectedFeature?.id == FeatureCatalog.backgroundRemover.id && state.installed) -> ToolScreen(
                    state = state,
                    onBack = { toolOpen = false; selectedFeature = null },
                    onChoosePhoto = { picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onRemoveBackground = vm::removeBackground,
                    onCancel = vm::cancelProcessing,
                    onCancelDownload = vm::cancelDownload,
                    onSaved = { vm.reportError("Saved to your photo library") },
                    onError = vm::reportError,
                    onPreparePhoto = vm::prepareBackgroundPhoto,
                    modifier = Modifier.padding(padding),
                )
                selectedFeature != null -> DetailsScreen(
                    feature = selectedFeature!!,
                    state = state,
                    onBack = { selectedFeature = null },
                    onDownload = vm::download,
                    onOpen = { toolOpen = true },
                    onCancel = vm::cancelDownload,
                    onSaved = { vm.reportError("Saved to your photo library") },
                    onError = vm::reportError,
                    modifier = androidx.compose.ui.Modifier.padding(padding),
                    onUpscalerDownload = vm::downloadUpscaler,
                    onUpscalerCancel = vm::cancelUpscalerDownload,
                    onSpeechDownload = vm::downloadSpeechModel,
                    onCancelSpeech = vm::cancelSpeechDownload,
                    onObjectDownload = vm::downloadObjectModel,
                    onCancelObject = vm::cancelObjectDownload,
                    onTranscriptionDownload = vm::downloadTranscriptionModel,
                    onCancelTranscription = vm::cancelTranscriptionDownload,
                )
                destination == Destination.EXPLORE -> ExploreScreen(
                    state = state,
                    listState = exploreListState,
                    onFeature = {
                        if (it.id == FeatureCatalog.backgroundRemover.id && state.installed) { selectedFeature = it; toolOpen = true }
                        else if (it.id == "image-upscaler" && state.upscalerInstalled) { selectedFeature = it; toolOpen = true }
                        else if (it.id == "noise-remover" && state.speechInstalled) { selectedFeature = it; toolOpen = true }
                        else if (it.id == "speech-to-text" && state.transcriptionInstalled) { selectedFeature = it; toolOpen = true }
                        else if (it.id == "object-remover" && state.objectInstalled) { selectedFeature = it; toolOpen = true }
                        else selectedFeature = it
                    },
                    onDownload = vm::download,
                    onOpen = { selectedFeature = it; toolOpen = true },
                    onCancel = vm::cancelDownload,
                    onUpscalerDownload = vm::downloadUpscaler,
                    onCancelUpscaler = vm::cancelUpscalerDownload,
                    onSpeechDownload = vm::downloadSpeechModel,
                    onCancelSpeech = vm::cancelSpeechDownload,
                    onObjectDownload = vm::downloadObjectModel,
                    onCancelObject = vm::cancelObjectDownload,
                    onTranscriptionDownload = vm::downloadTranscriptionModel,
                    onCancelTranscription = vm::cancelTranscriptionDownload,
                    modifier = androidx.compose.ui.Modifier.padding(padding),
                )
                destination == Destination.DOWNLOADS -> DownloadsScreen(
                    state = state,
                    onOpen = { selectedFeature = FeatureCatalog.backgroundRemover; toolOpen = true },
                    onDownload = vm::download,
                    onCancel = vm::cancelDownload,
                    onDelete = vm::deleteModel,
                    modifier = androidx.compose.ui.Modifier.padding(padding),
                    onOpenUpscaler = { selectedFeature = FeatureCatalog.all.first { it.id == "image-upscaler" }; toolOpen = true },
                    onDownloadUpscaler = vm::downloadUpscaler,
                    onCancelUpscaler = vm::cancelUpscalerDownload,
                    onDeleteUpscaler = vm::deleteUpscalerModel,
                    onOpenSpeech = { selectedFeature = FeatureCatalog.all.first { it.id == "noise-remover" }; toolOpen = true },
                    onDownloadSpeech = vm::downloadSpeechModel,
                    onCancelSpeech = vm::cancelSpeechDownload,
                    onDeleteSpeech = vm::deleteSpeechModel,
                    onOpenObject = { selectedFeature = FeatureCatalog.all.first { it.id == "object-remover" }; toolOpen = true },
                    onDownloadObject = vm::downloadObjectModel,
                    onCancelObject = vm::cancelObjectDownload,
                    onDeleteObject = vm::deleteObjectModel,
                    onOpenTranscription = { selectedFeature = FeatureCatalog.all.first { it.id == "speech-to-text" }; toolOpen = true },
                    onDownloadTranscription = vm::downloadTranscriptionModel,
                    onCancelTranscription = vm::cancelTranscriptionDownload,
                    onDeleteTranscription = vm::deleteTranscriptionModel,
                )
                else -> LocoraSettingsScreen(
                    state = state,
                    onTheme = vm::setThemePreference,
                    onDelete = vm::deleteModel,
                    onPrivacyPolicy = { showPrivacyPolicy = true },
                    onLicenses = { showLicenseNotices = true },
                    modifier = androidx.compose.ui.Modifier.padding(padding),
                    onDeleteUpscaler = vm::deleteUpscalerModel,
                    onDeleteSpeech = vm::deleteSpeechModel,
                    onDeleteObject = vm::deleteObjectModel,
                    onDeleteTranscription = vm::deleteTranscriptionModel,
                )
            }
        }
    }
}
