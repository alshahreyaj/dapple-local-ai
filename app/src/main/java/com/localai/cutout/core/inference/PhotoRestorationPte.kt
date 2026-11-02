package com.localai.cutout.core.inference

import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.Closeable

/**
 * Low-level experimental runners for the pinned keepsake PTEs.
 *
 * These classes intentionally accept/return raw FP32 NCHW tensors. Image decoding,
 * normalization, mask thresholding and tile composition are not applied here because
 * the publisher has not documented those export-specific contracts.
 */
class BopbtlScratchPte(modelPath: String) : Closeable {
    private val module = Module.load(modelPath)
    private var closed = false

    @Synchronized
    fun predictRaw(inputNchw: FloatArray): FloatArray {
        check(!closed) { "BOPBTL PTE is closed" }
        require(inputNchw.size == INPUT_ELEMENTS) { "BOPBTL input must contain $INPUT_ELEMENTS FP32 values" }
        val input = Tensor.fromBlob(inputNchw, INPUT_SHAPE)
        val outputs = module.forward(EValue.from(input))
        require(outputs.size == 1) { "Unexpected BOPBTL output count: ${outputs.size}" }
        return outputs.single().toTensor().dataAsFloatArray.also {
            require(it.size == OUTPUT_ELEMENTS) { "Unexpected BOPBTL output length: ${it.size}" }
            require(it.all(Float::isFinite)) { "BOPBTL output contains non-finite values" }
        }
    }

    @Synchronized override fun close() {
        if (!closed) {
            closed = true
            module.close()
        }
    }

    companion object {
        val INPUT_SHAPE = longArrayOf(1, 3, 384, 384)
        const val INPUT_ELEMENTS = 1 * 3 * 384 * 384
        const val OUTPUT_ELEMENTS = 1 * 1 * 384 * 384
    }
}

/** The exported LaMa wrapper accepts one four-channel NCHW tensor and returns RGB NCHW. */
class LamaRestorationPte(modelPath: String) : Closeable {
    private val module = Module.load(modelPath)
    private var closed = false

    @Synchronized
    fun inpaintRaw(inputNchw: FloatArray): FloatArray {
        check(!closed) { "LaMa PTE is closed" }
        require(inputNchw.size == INPUT_ELEMENTS) { "LaMa input must contain $INPUT_ELEMENTS FP32 values" }
        val input = Tensor.fromBlob(inputNchw, INPUT_SHAPE)
        val outputs = module.forward(EValue.from(input))
        require(outputs.size == 1) { "Unexpected LaMa output count: ${outputs.size}" }
        return outputs.single().toTensor().dataAsFloatArray.also {
            require(it.size == OUTPUT_ELEMENTS) { "Unexpected LaMa output length: ${it.size}" }
            require(it.all(Float::isFinite)) { "LaMa output contains non-finite values" }
        }
    }

    @Synchronized override fun close() {
        if (!closed) {
            closed = true
            module.close()
        }
    }

    companion object {
        val INPUT_SHAPE = longArrayOf(1, 4, 512, 512)
        const val INPUT_ELEMENTS = 1 * 4 * 512 * 512
        const val OUTPUT_ELEMENTS = 1 * 3 * 512 * 512
    }
}

/** Raw FP32 NCHW runner for the verified DDColor Tiny `[1,3,512,512] -> [1,2,512,512]` PTE. */
class DdcolorRestorationPte(modelPath: String) : Closeable {
    private val module = Module.load(modelPath)
    private var closed = false

    @Synchronized
    fun predictChroma(inputNchw: FloatArray): FloatArray {
        check(!closed) { "DDColor PTE is closed" }
        require(inputNchw.size == INPUT_ELEMENTS) { "DDColor input must contain $INPUT_ELEMENTS FP32 values" }
        val input = Tensor.fromBlob(inputNchw, INPUT_SHAPE)
        val outputs = module.forward(EValue.from(input))
        require(outputs.size == 1) { "Unexpected DDColor output count: ${outputs.size}" }
        return outputs.single().toTensor().dataAsFloatArray.also {
            require(it.size == OUTPUT_ELEMENTS) { "Unexpected DDColor output length: ${it.size}" }
            require(it.all(Float::isFinite)) { "DDColor output contains non-finite values" }
        }
    }

    @Synchronized override fun close() {
        if (!closed) {
            closed = true
            module.close()
        }
    }

    companion object {
        val INPUT_SHAPE = longArrayOf(1, 3, 512, 512)
        const val INPUT_ELEMENTS = 1 * 3 * 512 * 512
        const val OUTPUT_ELEMENTS = 1 * 2 * 512 * 512
    }
}
