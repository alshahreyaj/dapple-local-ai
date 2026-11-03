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
        AiFeature("background-remover", "Background Remover", "Remove a photo's background and save a PNG with a clear background.", ToolCategory.IMAGE, PreviewStyle.CUTOUT, "176 MB", "1.0", ModelSpecs.backgroundRemover.url, ModelSpecs.backgroundRemover.sha256, "On your phone", "Open-source license (Apache 2.0)", listOf("JPG", "PNG", "WebP"), "Android 8 or newer. Some phones may not support this tool.", ImplementationStatus.READY, previewResource = R.drawable.bg_remover_1, deviceCompatibilityOrder = 5),
        AiFeature("image-upscaler", "Photo Enhancer", "Bring more detail to low-resolution photos. Make them four times larger on your phone.", ToolCategory.IMAGE, PreviewStyle.UPSCALE, "17.5 MB", "1.0", ModelSpecs.imageUpscaler.url, ModelSpecs.imageUpscaler.sha256, "On your phone", "Open-source license (BSD 3-Clause)", listOf("JPG", "PNG", "WebP"), "Android 8 or newer. Speed depends on your phone.", ImplementationStatus.READY, previewResource = R.drawable.enhancer_1, deviceCompatibilityOrder = 3),
        AiFeature("noise-remover", "Speech Noise Remover", "Make voices easier to hear by reducing background noise on your phone.", ToolCategory.AUDIO, PreviewStyle.SOUND, "19.6 MB", "1.0", ModelSpecs.speechNoiseRemover.url, null, "On your phone", "Open-source license (Apache 2.0)", listOf("WAV", "MP3", "M4A", "FLAC", "OGG"), "Supported file types depend on your phone.", ImplementationStatus.READY, previewResource = R.drawable.noice_reducer_1, deviceCompatibilityOrder = 1),
        AiFeature("document-scanner-ocr", "Document Scanner & OCR", "Scan, clean up and search documents. Your pages and text stay on your phone.", ToolCategory.IMAGE, PreviewStyle.DOCUMENT, "12.9 MB", "PP-OCRv5 Mobile", ModelSpecs.documentOcr.artifacts.first().url, "SHA-256 checked for all model files", "ONNX Runtime · OpenCV", "Apache 2.0", listOf("JPG", "PNG", "WebP"), "Android 8 or newer. Recognition supports English and German. Export searchable PDFs or TXT files.", ImplementationStatus.READY, previewResource = R.drawable.scanner_1, deviceCompatibilityOrder = 2),
        AiFeature("object-remover", "Object Remover", "Brush over unwanted objects and fill the area naturally.", ToolCategory.IMAGE, PreviewStyle.ERASE, "174 MB", "0.65.0", ModelSpecs.objectRemover.url, ModelSpecs.objectRemover.sha256, "On your phone", "Open-source license (Apache 2.0)", listOf("JPG", "PNG", "WebP"), "Android 8 or newer. Results and speed depend on your phone.", ImplementationStatus.READY, previewResource = R.drawable.object_remover_1, deviceCompatibilityOrder = 4),
        AiFeature("photo-restoration", "Photo Restoration", "Repair scratches, bring faded black-and-white photos back to color, and enhance detail on your phone.", ToolCategory.IMAGE, PreviewStyle.RESTORE, "488 MB", "LiteRT · Real-ESRGAN General x4v3", ModelSpecs.restorationScratch.url, ModelSpecs.restorationScratch.sha256, "On your phone · uses available acceleration", "BOPBTL (MIT), LaMa and DDColor (Apache-2.0), Real-ESRGAN x4v3 (BSD 3-Clause)", listOf("JPG", "PNG", "WebP"), "About 488 MB for all Photo Restoration models. Every restoration includes colorization and detail enhancement.", ImplementationStatus.READY, previewResource = R.drawable.photo_restoration_1, deviceCompatibilityOrder = 6),
        AiFeature("speech-to-text", "Speech-to-Text", "Turn voice recordings into editable text in 15 common languages, right on your phone.", ToolCategory.AUDIO, PreviewStyle.TRANSCRIBE, "161 MB", "Whisper Base", ModelSpecs.speechToText.url, "SHA-256 checked for each model file", "On your phone", "Open-source licenses (MIT and Apache 2.0)", listOf("WAV", "MP3", "M4A", "FLAC", "OGG"), "Choose from 15 common languages or use automatic detection. Results depend on language and recording quality. Recordings can be up to five minutes.", ImplementationStatus.READY, previewResource = R.drawable.speech_to_text_1, deviceCompatibilityOrder = 2),
        AiFeature("offline-translator", "Offline Translator", "Translate text without an internet connection.", ToolCategory.TEXT, PreviewStyle.TRANSLATE, previewResource = R.drawable.offline_translator_1),
        AiFeature("video-background-remover", "Video Background Remover", "Remove or replace video backgrounds using AI.", ToolCategory.VIDEO, PreviewStyle.VIDEO_CUTOUT, previewResource = R.drawable.video_bg_remover_1),
        AiFeature("video-enhancer", "Video Enhancer", "Upscale videos, improve clarity and reduce noise.", ToolCategory.VIDEO, PreviewStyle.VIDEO_ENHANCE, previewResource = R.drawable.video_enhancer_1),
    )
    val backgroundRemover = all.first()
}
