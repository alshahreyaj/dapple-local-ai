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
    /** PP-OCRv5 Mobile Latin detector + English/German recognizer + official alphabet. */
    val documentOcr = ModelSpec(
        id = "document-scanner-ocr",
        fileName = "document-ocr-ppocrv5-latin",
        sizeBytes = 12_871_157L,
        sha256 = "",
        url = "",
        downloadSizeBytes = 12_871_157L,
        downloadSha256 = "",
        verifySize = false,
        storageEstimateBytes = 13_500_000L,
        artifacts = listOf(
            ModelArtifact("det.onnx", "https://huggingface.co/buckets/xeneva-studio/localAI/resolve/document-ocr/det.onnx?download=true", "a431985659dc921974177a95adcfbb90fd9e51989a5e04d70d0b75f597b6e61d", 4_826_518L),
            ModelArtifact("rec.onnx", "https://huggingface.co/buckets/xeneva-studio/localAI/resolve/document-ocr/rec.onnx?download=true", "7888113072263cb471b93f66dd5e2ad70548dc526fa1ace760d0d973dd121498", 8_042_023L),
            ModelArtifact("latin_dict.txt", "https://huggingface.co/buckets/xeneva-studio/localAI/resolve/document-ocr/latin_dict.txt?download=true", "ccbcc45730b3fbbd9050c5bc74db6a99067141ef1035e3d14889a84a6b9b1aff", 2_616L),
        ),
    )
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
        url = "https://huggingface.co/buckets/xeneva-studio/localAI/resolve/speech-to-text/base-encoder.int8.onnx?download=true",
        downloadSizeBytes = 160_900_000L,
        downloadSha256 = "",
        verifySize = false,
        storageEstimateBytes = 340L * 1024 * 1024,
        artifacts = listOf(
            ModelArtifact("base-encoder.int8.onnx", "https://huggingface.co/buckets/xeneva-studio/localAI/resolve/speech-to-text/base-encoder.int8.onnx?download=true", "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11"),
            ModelArtifact("base-decoder.int8.onnx", "https://huggingface.co/buckets/xeneva-studio/localAI/resolve/speech-to-text/base-decoder.int8.onnx?download=true", "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d"),
            ModelArtifact("base-tokens.txt", "https://huggingface.co/buckets/xeneva-studio/localAI/resolve/speech-to-text/base-tokens.txt?download=true", "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"),
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
    // Pinned to the verified keepsake-models revision. These are separate downloads so
    // scratch detection can be evaluated before the larger LaMa model is requested.
    val restorationScratch = ModelSpec(
        id = "photo-restoration-scratch",
        fileName = "bopbtl_scratch_t384_xnnpack_int8.pte",
        sizeBytes = 37_772_036L,
        sha256 = "651299699f28ce2e7dedadcc4732137d7731d5cd3b21aeaa1395fbdfb8b232d5",
        url = "https://huggingface.co/buckets/xeneva-studio/localAI/resolve/photo-restoration/bopbtl_scratch_t384_xnnpack_int8.pte?download=true",
    )
    val restorationLama = ModelSpec(
        id = "photo-restoration-lama",
        fileName = "lama_big_t512_xnnpack.pte",
        sizeBytes = 204_361_100L,
        sha256 = "5a808536fcbe0624bb81f0bb8ad20fca051efd640e994a720a78d066204947f4",
        url = "https://huggingface.co/buckets/xeneva-studio/localAI/resolve/photo-restoration/lama_big_t512_xnnpack.pte?download=true",
    )
    val restorationColorizer = ModelSpec(
        id = "photo-restoration-colorizer",
        fileName = "ddcolor_paper_tiny_t512_xnnpack.pte",
        sizeBytes = 242_297_736L,
        sha256 = "a582fb6ae0d65c2c6424efcd53a8da38a3c38c5a22f7288b48aa29abbcfe6c89",
        url = "https://huggingface.co/buckets/xeneva-studio/localAI/resolve/photo-restoration/ddcolor_paper_tiny_t512_xnnpack.pte?download=true",
    )
    // LiteRT Community Real-ESRGAN General x4v3, pinned to the verified export revision.
    val restorationEnhancer = ModelSpec(
        id = "photo-restoration-enhancer",
        fileName = "realesr_general_x4v3.tflite",
        sizeBytes = 3_549_456L,
        sha256 = "6d597b46d812ada42bdde641ec6e78c0ef0b78ff5a825fcfd74b9a1b3bcbe9a4",
        url = "https://huggingface.co/buckets/xeneva-studio/localAI/resolve/photo-restoration/realesr_general_x4v3.tflite?download=true",
    )
    val all = listOf(backgroundRemover, imageUpscaler, speechNoiseRemover, speechToText, objectRemover, documentOcr, restorationScratch, restorationLama, restorationColorizer, restorationEnhancer)
    fun byId(id: String) = all.firstOrNull { it.id == id }
}
