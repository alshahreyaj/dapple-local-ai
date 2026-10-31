package com.localai.cutout.core.inference

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CancellationException

data class TranscriptTimeMark(val timeSeconds: Float, val text: String)
data class WhisperTranscript(val text: String, val language: String, val timeMarks: List<TranscriptTimeMark>)

/** CPU sherpa-onnx wrapper for the multilingual Whisper Base INT8 ONNX files. */
class WhisperSpeechToText(modelDirectory: File, language: String = "") : Closeable {
    private val recognizer: OfflineRecognizer

    init {
        require(File(modelDirectory, ENCODER).isFile && File(modelDirectory, DECODER).isFile && File(modelDirectory, TOKENS).isFile) {
            "The speech model is incomplete. Download it again from Downloads."
        }
        val model = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(
                encoder = File(modelDirectory, ENCODER).absolutePath,
                decoder = File(modelDirectory, DECODER).absolutePath,
                language = language, // Empty selects Whisper's built-in language detection for multilingual models.
                task = "transcribe",
                enableTokenTimestamps = true,
                enableSegmentTimestamps = true,
            ),
            tokens = File(modelDirectory, TOKENS).absolutePath,
            numThreads = minOf(Runtime.getRuntime().availableProcessors().coerceAtLeast(1), 4),
            provider = "cpu",
            modelType = "whisper",
        )
        recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
                modelConfig = model,
            ),
        )
    }

    fun transcribe(samples16k: FloatArray, shouldCancel: () -> Boolean, onProgress: (Int, Int) -> Unit): WhisperTranscript {
        require(samples16k.isNotEmpty()) { "This recording is empty." }
        val samplesPerWindow = 30 * 16_000
        val windowCount = (samples16k.size + samplesPerWindow - 1) / samplesPerWindow
        val textParts = mutableListOf<String>()
        val marks = mutableListOf<TranscriptTimeMark>()
        var detectedLanguage = ""
        repeat(windowCount) { windowIndex ->
            if (shouldCancel()) throw CancellationException("Transcription cancelled")
            val start = windowIndex * samplesPerWindow
            val end = minOf(samples16k.size, start + samplesPerWindow)
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(samples16k.copyOfRange(start, end), 16_000)
                recognizer.decode(stream)
                val result = recognizer.getResult(stream)
                if (result.text.isNotBlank()) textParts += result.text.trim()
                if (detectedLanguage.isBlank()) detectedLanguage = result.lang
                result.tokens.indices.forEach { index ->
                    val token = result.tokens[index]
                    val time = result.timestamps.getOrNull(index) ?: return@forEach
                    val visible = token.replace("▁", " ").trim()
                    if (visible.isNotBlank() && !token.startsWith("<|") && time.isFinite() && time >= 0f) marks += TranscriptTimeMark(start / 16_000f + time, visible)
                }
            } finally {
                stream.release()
            }
            onProgress(windowIndex + 1, windowCount)
        }
        return WhisperTranscript(textParts.joinToString(" ").replace(Regex("\\s+"), " ").trim(), detectedLanguage, marks)
    }

    override fun close() = recognizer.release()

    companion object {
        const val ENCODER = "base-encoder.int8.onnx"
        const val DECODER = "base-decoder.int8.onnx"
        const val TOKENS = "base-tokens.txt"
    }
}
