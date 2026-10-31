package com.localai.cutout.catalog

import androidx.annotation.DrawableRes
import com.localai.cutout.R
import com.localai.cutout.core.model.ModelSpecs

enum class ToolCategory(val label: String) { IMAGE("Image"), AUDIO("Audio"), TEXT("Text"), VIDEO("Video") }
enum class ImplementationStatus { READY, COMING_SOON }
enum class PreviewStyle { CUTOUT, UPSCALE, ERASE, ENHANCE, COLOR, SOUND, TRANSCRIBE, VIDEO, DOCUMENT, RESTORE, TRANSLATE, VIDEO_CUTOUT, VIDEO_ENHANCE }

data class AiFeature(
    val id: String,
    val title: String,
    val description: String,
    val category: ToolCategory,
    val preview: PreviewStyle,
    val modelSize: String? = null,
    val modelVersion: String? = null,
    val downloadUrl: String? = null,
    val checksum: String? = null,
    val runtime: String? = null,
    val license: String? = null,
    val supportedFormats: List<String> = emptyList(),
    val requirements: String? = null,
    val status: ImplementationStatus = ImplementationStatus.COMING_SOON,
    val updateAvailable: Boolean = false,
    @DrawableRes val previewResource: Int? = null,
    /** Relative broad device compatibility; lower values appear first among available tools. */
    val deviceCompatibilityOrder: Int = Int.MAX_VALUE,
)

object FeatureCatalog {
    val all = listOf(
        AiFeature("background-remover", "Background Remover", "Remove a photo's background and save a PNG with a clear background.", ToolCategory.IMAGE, PreviewStyle.CUTOUT, "176 MB", "1.0", ModelSpecs.backgroundRemover.url, ModelSpecs.backgroundRemover.sha256, "Your phone", "Open source · Apache 2.0", listOf("JPG", "PNG", "WebP"), "Android 8 or newer. Some phones may not be supported.", ImplementationStatus.READY, previewResource = R.drawable.bg_remover_1, deviceCompatibilityOrder = 5),
        AiFeature("image-upscaler", "Photo Enhancer", "Bring more detail to low-resolution photos. Make them four times larger on your phone.", ToolCategory.IMAGE, PreviewStyle.UPSCALE, "17.5 MB", "1.0", ModelSpecs.imageUpscaler.url, ModelSpecs.imageUpscaler.sha256, "Your phone", "BSD 3-Clause · Real-ESRGAN", listOf("JPG", "PNG", "WebP"), "Android 8 or newer. Processing speed depends on your phone.", ImplementationStatus.READY, previewResource = R.drawable.enhancer_1, deviceCompatibilityOrder = 3),
        AiFeature("noise-remover", "Speech Noise Remover", "Make voices easier to hear by reducing background noise on your phone.", ToolCategory.AUDIO, PreviewStyle.SOUND, "19.6 MB", "1.0", ModelSpecs.speechNoiseRemover.url, null, "Your phone", "Open source · Apache 2.0", listOf("WAV", "MP3", "M4A", "FLAC", "OGG"), "Audio format support varies by phone.", ImplementationStatus.READY, previewResource = R.drawable.noice_reducer_1, deviceCompatibilityOrder = 1),
        AiFeature("document-scanner-ocr", "Document Scanner & OCR", "Scan documents, extract text and create searchable PDFs.", ToolCategory.IMAGE, PreviewStyle.DOCUMENT, previewResource = R.drawable.scanner_1),
        AiFeature("object-remover", "Object Remover", "Brush over unwanted objects and fill the area naturally.", ToolCategory.IMAGE, PreviewStyle.ERASE, "174 MB", "0.65.0", ModelSpecs.objectRemover.url, ModelSpecs.objectRemover.sha256, "Your phone", "LaMa-Dilated · Apache 2.0 source", listOf("JPG", "PNG", "WebP"), "Android 8 or newer. Processing speed and supported acceleration vary by phone.", ImplementationStatus.READY, previewResource = R.drawable.object_remover_1, deviceCompatibilityOrder = 4),
        AiFeature("photo-restoration", "Photo Restoration", "Repair old, faded and damaged photographs.", ToolCategory.IMAGE, PreviewStyle.RESTORE, previewResource = R.drawable.photo_restoration_1),
        AiFeature("speech-to-text", "Speech-to-Text", "Turn voice recordings into editable text in a range of languages, right on your phone.", ToolCategory.AUDIO, PreviewStyle.TRANSCRIBE, "161 MB", "Whisper Base INT8", ModelSpecs.speechToText.url, "SHA-256 checked for each model file", "sherpa-onnx · on-device CPU", "Whisper · MIT; sherpa-onnx · Apache 2.0", listOf("WAV", "MP3", "M4A", "FLAC", "OGG"), "Choose from Whisper's multilingual language list or use automatic language detection. Accuracy varies by language and recording quality. Recordings up to five minutes.", ImplementationStatus.READY, previewResource = R.drawable.speech_to_text_1, deviceCompatibilityOrder = 2),
        AiFeature("offline-translator", "Offline Translator", "Translate text without an internet connection.", ToolCategory.TEXT, PreviewStyle.TRANSLATE, previewResource = R.drawable.offline_translator_1),
        AiFeature("video-background-remover", "Video Background Remover", "Remove or replace video backgrounds using AI.", ToolCategory.VIDEO, PreviewStyle.VIDEO_CUTOUT, previewResource = R.drawable.video_bg_remover_1),
        AiFeature("video-enhancer", "Video Enhancer", "Upscale videos, improve clarity and reduce noise.", ToolCategory.VIDEO, PreviewStyle.VIDEO_ENHANCE, previewResource = R.drawable.video_enhancer_1),
    )
    val backgroundRemover = all.first()
}
