package com.localai.cutout.core.inference

/** Curated language choices for the multilingual Whisper Base model. */
data class WhisperLanguage(val code: String, val name: String)

object WhisperLanguages {
    const val AUTO = "auto"

    // Keep codes aligned with OpenAI Whisper's multilingual tokenizer. The
    // tokenizer supports more languages, but the selector focuses on common,
    // well-represented languages for this Base-sized model.
    val supported = listOf(
        WhisperLanguage("ar", "Arabic"),
        WhisperLanguage("de", "German"),
        WhisperLanguage("en", "English"),
        WhisperLanguage("es", "Spanish"),
        WhisperLanguage("fr", "French"),
        WhisperLanguage("hi", "Hindi"),
        WhisperLanguage("it", "Italian"),
        WhisperLanguage("ja", "Japanese"),
        WhisperLanguage("ko", "Korean"),
        WhisperLanguage("nl", "Dutch"),
        WhisperLanguage("pl", "Polish"),
        WhisperLanguage("pt", "Portuguese"),
        WhisperLanguage("ru", "Russian"),
        WhisperLanguage("tr", "Turkish"),
        WhisperLanguage("zh", "Chinese"),
    ).sortedBy { it.name }

    fun label(code: String?): String? = supported.firstOrNull { it.code == code }?.name
}
