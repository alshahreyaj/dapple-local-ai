package com.localai.cutout.core.model

/** Download metadata is deliberately separate from feature presentation and inference. */
data class ModelSpec(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val url: String,
    val downloadSizeBytes: Long = sizeBytes,
    val downloadSha256: String = sha256,
    val archiveEntry: String? = null,
    val verifySize: Boolean = true,
    val storageEstimateBytes: Long = maxOf(sizeBytes, downloadSizeBytes),
    /** Multi-file models are installed as a directory; each artifact is independently verified. */
    val artifacts: List<ModelArtifact> = emptyList(),
)

data class ModelArtifact(
    val fileName: String,
    val url: String,
    val sha256: String,
    /** Zero means the upstream host does not publish a byte-exact size. */
    val sizeBytes: Long = 0L,
)

object ModelSpecs {
    val backgroundRemover = ModelSpec(
        "background-remover", "ormbg.tflite", 176_240_148L,
        "f6e359cf35c55e7bf216eb3141f6d76828667a5e8e67a929f53e9f7f38fae030",
        "https://huggingface.co/buckets/xeneva-studio/localAI/resolve/bg_remover_1.tflite?download=true",
    )
    val imageUpscaler = ModelSpec(
        "image-upscaler", "enhancer_1.tflite", 17_549_184L,
        "7a76b1ca65f179919e64b62f82d05b735eec5410a190cc6a156785cedc9ffb69",
        "https://huggingface.co/buckets/xeneva-studio/localAI/resolve/enhancer_1.tflite?download=true",
    )
    val speechNoiseRemover = ModelSpec(
        "noise-remover", "noise_reducer.tflite", 19_600_000L, "",
        "https://huggingface.co/buckets/xeneva-studio/localAI/resolve/noise_reducer.tflite?download=true",
        downloadSizeBytes = 19_600_000L, downloadSha256 = "", verifySize = false,
    )
    val speechToText = ModelSpec(
        id = "speech-to-text",
        fileName = "whisper-base-int8",
        sizeBytes = 160_900_000L,
        sha256 = "",
        url = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/bb53ee2/base-encoder.int8.onnx",
        downloadSizeBytes = 160_900_000L,
        downloadSha256 = "",
        verifySize = false,
        storageEstimateBytes = 340L * 1024 * 1024,
        artifacts = listOf(
            ModelArtifact("base-encoder.int8.onnx", "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/bb53ee2/base-encoder.int8.onnx", "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11"),
            ModelArtifact("base-decoder.int8.onnx", "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/08ea75619ad84db511cd6fbf16f8c197a7d0022a/base-decoder.int8.onnx", "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d"),
            ModelArtifact("base-tokens.txt", "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/bb53ee2/base-tokens.txt", "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"),
        ),
    )
    // Qualcomm AI Hub LaMa-Dilated TFLite float export (512x512, approx. 174 MB).
    // Size is an estimate from the model card; integrity is enforced by SHA-256.
    val objectRemover = ModelSpec(
        "object-remover", "object_remover.tflite", 0L,
        "0d453a9a6fafdd52db8b11834799adc069b99465d1a65fc3df5b954773fa84d3",
        "https://huggingface.co/buckets/xeneva-studio/localAI/resolve/object_remover.tflite?download=true",
        downloadSizeBytes = 0L,
        downloadSha256 = "0d453a9a6fafdd52db8b11834799adc069b99465d1a65fc3df5b954773fa84d3",
        verifySize = false,
        storageEstimateBytes = 174L * 1024 * 1024,
    )
    val all = listOf(backgroundRemover, imageUpscaler, speechNoiseRemover, speechToText, objectRemover)
    fun byId(id: String) = all.firstOrNull { it.id == id }
}
