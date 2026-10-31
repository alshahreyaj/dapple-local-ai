package com.localai.cutout.core.inference

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.FileInputStream

/** Qualcomm AI Hub Real-ESRGAN x4plus W8A8, RGB uint8 NHWC 128² -> 512², range 0..255. */
class RealEsrganUpscaler private constructor(private val runner: InferenceRunner, val backend: String) : AutoCloseable {
    companion object {
        const val TILE = 128
        const val SCALE = 4
        const val OVERLAP = 32
        private const val CHANNELS = 3
        private const val INPUT_BYTES = TILE * TILE * CHANNELS
        private const val OUTPUT_BYTES = TILE * SCALE * TILE * SCALE * CHANNELS

        fun fromFile(path: String): RealEsrganUpscaler {
            val candidates = buildList {
                if (hasQualcommNpuRuntime()) add("Qualcomm NPU" to { createCompiledRunner(path, Accelerator.NPU) })
                add("GPU" to { createCompiledRunner(path, Accelerator.GPU) })
                add("CPU fallback" to { createCompiledRunner(path, Accelerator.CPU) })
            }
            var lastError: Throwable? = null
            for ((label, create) in candidates) {
                try { return RealEsrganUpscaler(create(), label) }
                catch (error: Throwable) {
                    lastError?.addSuppressed(error)
                    lastError = error
                    Log.w("LocoraUpscaler", "$label initialization or warmup failed", error)
                }
            }
            throw IllegalStateException("Could not initialize Real-ESRGAN on NPU, GPU, or CPU", lastError)
        }

        private fun hasQualcommNpuRuntime(): Boolean {
            if (Build.SUPPORTED_ABIS.none { it == "arm64-v8a" }) return false
            val socManufacturer = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER.orEmpty() else ""
            return (Build.HARDWARE + " " + Build.BOARD + " " + socManufacturer)
                .contains("qcom", ignoreCase = true) || socManufacturer.contains("qualcomm", ignoreCase = true)
        }

        private fun createCompiledRunner(path: String, accelerator: Accelerator): InferenceRunner {
            val model = CompiledModel.create(path, CompiledModel.Options(accelerator), null)
            val inputs = model.createInputBuffers()
            val outputs = model.createOutputBuffers()
            try {
                val runner = CompiledRunner(model, inputs, outputs)
                runner.run(ByteArray(INPUT_BYTES)) // Confirm this accelerator can execute the W8A8 graph.
                return runner
            } catch (error: Throwable) {
                inputs.forEach { runCatching { it.close() } }
                outputs.forEach { runCatching { it.close() } }
                runCatching { model.close() }
                throw error
            }
        }
    }
    private val tilePixels = IntArray(TILE * TILE)
    private val inputBytes = ByteArray(INPUT_BYTES)
    @Volatile private var cancelled = false
    @Volatile private var closed = false
    var inferenceMs: Long = 0L
        private set

    @Synchronized fun process(source: Bitmap, onProgress: (Int, Int) -> Unit = { _, _ -> }): Bitmap {
        check(!closed) { "Upscaler is closed" }
        require(!source.isRecycled && source.width > 0 && source.height > 0) { "Choose a valid image" }
        val outWidth = Math.multiplyExact(source.width, SCALE)
        val outHeight = Math.multiplyExact(source.height, SCALE)
        val outputPixels = outWidth.toLong() * outHeight
        require(outputPixels * 4L <= Runtime.getRuntime().maxMemory() / 3) {
            "This image is too large for a 4× result in available memory. Choose a smaller image."
        }
        val result = try { Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888) }
        catch (e: OutOfMemoryError) { throw IllegalStateException("Not enough memory for a 4× result. Choose a smaller image.", e) }
        val xs = tileStarts(source.width)
        val ys = tileStarts(source.height)
        val total = xs.size * ys.size
        var done = 0
        val started = System.nanoTime()
        cancelled = false
        try {
            for ((yi, sy) in ys.withIndex()) for ((xi, sx) in xs.withIndex()) {
                if (cancelled) throw InterruptedException("Upscaling cancelled")
                prepareInput(source, sx, sy)
                val tileOutput = runner.run(inputBytes)
                blendTile(result, tileOutput, sx, sy, source.width, source.height, xi > 0, xi < xs.size - 1, yi > 0, yi < ys.size - 1)
                onProgress(++done, total)
            }
            val row = IntArray(outWidth)
            for (y in 0 until outHeight) {
                result.getPixels(row, 0, outWidth, 0, y, outWidth, 1)
                for (x in row.indices) row[x] = row[x] or (0xff shl 24)
                result.setPixels(row, 0, outWidth, 0, y, outWidth, 1)
            }
            inferenceMs = (System.nanoTime() - started) / 1_000_000
            return result
        } catch (e: Throwable) {
            result.recycle()
            throw e
        } finally { cancelled = false }
    }

    private fun prepareInput(source: Bitmap, startX: Int, startY: Int) {
        val maxX = source.width - 1; val maxY = source.height - 1
        for (y in 0 until TILE) for (x in 0 until TILE) {
            val sx = (startX + x).coerceIn(0, maxX); val sy = (startY + y).coerceIn(0, maxY)
            tilePixels[y * TILE + x] = source.getPixel(sx, sy)
        }
        var index = 0
        tilePixels.forEach { pixel ->
            inputBytes[index++] = ((pixel shr 16) and 255).toByte()
            inputBytes[index++] = ((pixel shr 8) and 255).toByte()
            inputBytes[index++] = (pixel and 255).toByte()
        }
    }

    /** The model output is uint8 NHWC, RGB; alpha holds blend weights until the final pass. */
    private fun blendTile(bitmap: Bitmap, rgb: ByteArray, startX: Int, startY: Int, imageW: Int, imageH: Int, left: Boolean, right: Boolean, top: Boolean, bottom: Boolean) {
        val width = minOf(TILE, imageW - startX) * SCALE
        val height = minOf(TILE, imageH - startY) * SCALE
        val originX = startX * SCALE; val originY = startY * SCALE
        val existing = IntArray(width * height)
        bitmap.getPixels(existing, 0, width, originX, originY, width, height)
        var rgbIndex = 0
        for (y in 0 until height) for (x in 0 until width) {
            val weight = maxOf(1, feather(x, width, left, right) * feather(y, height, top, bottom) / 32)
            val existingIndex = y * width + x
            val old = existing[existingIndex]; val oldWeight = old ushr 24
            val newR = rgb[rgbIndex++].toInt() and 255
            val newG = rgb[rgbIndex++].toInt() and 255
            val newB = rgb[rgbIndex++].toInt() and 255
            val sum = oldWeight + weight
            fun blend(previous: Int, next: Int) = if (oldWeight == 0) next else (previous * oldWeight + next * weight) / sum
            existing[existingIndex] = (minOf(sum, 255) shl 24) or
                (blend((old shr 16) and 255, newR) shl 16) or
                (blend((old shr 8) and 255, newG) shl 8) or blend(old and 255, newB)
        }
        bitmap.setPixels(existing, 0, width, originX, originY, width, height)
    }

    private fun feather(position: Int, extent: Int, hasNeighborBefore: Boolean, hasNeighborAfter: Boolean): Int {
        val overlap = OVERLAP * SCALE
        val before = if (hasNeighborBefore) ((position + 1).toFloat() / overlap).coerceIn(0f, 1f) else 1f
        val after = if (hasNeighborAfter) ((extent - position).toFloat() / overlap).coerceIn(0f, 1f) else 1f
        return (minOf(before, after) * 32).toInt().coerceAtLeast(1)
    }

    private fun tileStarts(dimension: Int): List<Int> {
        if (dimension <= TILE) return listOf(0)
        val last = dimension - TILE
        val starts = mutableListOf(0); var current = 0
        while (current + TILE < dimension) {
            val next = current + TILE - OVERLAP
            if (next >= last) break
            current = next
            starts += current
        }
        if (starts.last() != last) starts += last
        return starts
    }

    fun cancel() { cancelled = true }
    @Synchronized override fun close() {
        if (closed) return
        closed = true
        runCatching { runner.close() }
    }
}

private interface InferenceRunner : AutoCloseable {
    fun run(input: ByteArray): ByteArray
}

private class CompiledRunner(
    private val model: CompiledModel,
    private val inputs: List<TensorBuffer>,
    private val outputs: List<TensorBuffer>,
) : InferenceRunner {
    override fun run(input: ByteArray): ByteArray {
        inputs[0].writeInt8(input)
        model.run(inputs, outputs)
        return outputs[0].readInt8()
    }
    override fun close() {
        inputs.forEach { it.close() }
        outputs.forEach { it.close() }
        model.close()
    }
}
