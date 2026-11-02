package com.localai.cutout.core.inference

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import java.util.concurrent.atomic.AtomicBoolean

/** LiteRT Community Real-ESRGAN General x4v3, RGB float NHWC 128² -> RGB float NCHW 512². */
class RealEsrganGeneralX4v3 private constructor(
    private val runner: Runner,
    val backend: String,
) : AutoCloseable {
    companion object {
        const val TILE = 128
        const val SCALE = 4
        const val OVERLAP = 32
        private const val CHANNELS = 3
        private const val INPUT_SIZE = TILE * TILE * CHANNELS
        private const val OUTPUT_SIDE = TILE * SCALE
        private const val OUTPUT_PLANE = OUTPUT_SIDE * OUTPUT_SIDE
        private const val OUTPUT_SIZE = CHANNELS * OUTPUT_PLANE

        fun fromFile(path: String): RealEsrganGeneralX4v3 {
            val candidates = buildList {
                if (hasQualcommNpuRuntime()) add("Qualcomm NPU" to { createRunner(path, Accelerator.NPU) })
                add("GPU" to { createRunner(path, Accelerator.GPU) })
                add("CPU fallback" to { createRunner(path, Accelerator.CPU) })
            }
            var lastError: Throwable? = null
            for ((label, create) in candidates) {
                try {
                    return RealEsrganGeneralX4v3(create(), label)
                } catch (error: Throwable) {
                    lastError?.addSuppressed(error)
                    lastError = error
                    Log.w("DappleRestorationEnhancer", "$label initialization or warmup failed", error)
                }
            }
            throw IllegalStateException("Could not initialize the Photo Restoration enhancer", lastError)
        }

        private fun hasQualcommNpuRuntime(): Boolean {
            if (Build.SUPPORTED_ABIS.none { it == "arm64-v8a" }) return false
            val manufacturer = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER.orEmpty() else ""
            return (Build.HARDWARE + " " + Build.BOARD + " " + manufacturer).let {
                it.contains("qcom", ignoreCase = true) || it.contains("qualcomm", ignoreCase = true)
            }
        }

        private fun createRunner(path: String, accelerator: Accelerator): Runner {
            val model = CompiledModel.create(path, CompiledModel.Options(accelerator), null)
            val inputs = model.createInputBuffers()
            val outputs = model.createOutputBuffers()
            try {
                val runner = Runner(model, inputs, outputs)
                check(runner.run(FloatArray(INPUT_SIZE)).size == OUTPUT_SIZE) {
                    "Photo Restoration enhancer returned an unexpected output tensor."
                }
                return runner
            } catch (error: Throwable) {
                inputs.forEach { runCatching { it.close() } }
                outputs.forEach { runCatching { it.close() } }
                runCatching { model.close() }
                throw error
            }
        }
    }

    private val cancelled = AtomicBoolean(false)
    @Volatile private var closed = false
    var inferenceMs: Long = 0L
        private set

    @Synchronized
    fun process(source: Bitmap, onProgress: (Int, Int) -> Unit = { _, _ -> }): Bitmap {
        check(!closed) { "Photo Restoration enhancer is closed" }
        require(!source.isRecycled && source.width > 0 && source.height > 0) { "Choose a valid image" }
        val outputWidth = Math.multiplyExact(source.width, SCALE)
        val outputHeight = Math.multiplyExact(source.height, SCALE)
        require(outputWidth.toLong() * outputHeight * 4L <= Runtime.getRuntime().maxMemory() / 3) {
            "This photo is too large for a 4× restored result in available memory. Choose a smaller photo."
        }
        val result = try {
            Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        } catch (error: OutOfMemoryError) {
            throw IllegalStateException("Not enough memory for the 4× restored result. Choose a smaller photo.", error)
        }
        val xs = tileStarts(source.width)
        val ys = tileStarts(source.height)
        val total = xs.size * ys.size
        val input = FloatArray(INPUT_SIZE)
        val pixels = IntArray(TILE * TILE)
        val started = System.nanoTime()
        cancelled.set(false)
        try {
            var completed = 0
            for ((yi, sy) in ys.withIndex()) for ((xi, sx) in xs.withIndex()) {
                if (cancelled.get()) throw InterruptedException("Photo restoration cancelled")
                prepareInput(source, sx, sy, pixels, input)
                val output = runner.run(input)
                blendTile(result, output, sx, sy, source.width, source.height, xi > 0, xi < xs.size - 1, yi > 0, yi < ys.size - 1)
                onProgress(++completed, total)
            }
            val row = IntArray(outputWidth)
            for (y in 0 until outputHeight) {
                result.getPixels(row, 0, outputWidth, 0, y, outputWidth, 1)
                for (x in row.indices) row[x] = row[x] or (0xff shl 24)
                result.setPixels(row, 0, outputWidth, 0, y, outputWidth, 1)
            }
            inferenceMs = (System.nanoTime() - started) / 1_000_000
            return result
        } catch (error: Throwable) {
            result.recycle()
            throw error
        } finally {
            cancelled.set(false)
        }
    }

    private fun prepareInput(source: Bitmap, startX: Int, startY: Int, pixels: IntArray, input: FloatArray) {
        for (y in 0 until TILE) for (x in 0 until TILE) {
            pixels[y * TILE + x] = source.getPixel((startX + x).coerceAtMost(source.width - 1), (startY + y).coerceAtMost(source.height - 1))
        }
        var index = 0
        for (pixel in pixels) {
            input[index++] = ((pixel shr 16) and 255) / 255f
            input[index++] = ((pixel shr 8) and 255) / 255f
            input[index++] = (pixel and 255) / 255f
        }
    }

    /** Output is NCHW RGB float [0,1], as specified by the published LiteRT export. */
    private fun blendTile(
        bitmap: Bitmap,
        output: FloatArray,
        startX: Int,
        startY: Int,
        imageWidth: Int,
        imageHeight: Int,
        left: Boolean,
        right: Boolean,
        top: Boolean,
        bottom: Boolean,
    ) {
        val width = minOf(TILE, imageWidth - startX) * SCALE
        val height = minOf(TILE, imageHeight - startY) * SCALE
        val originX = startX * SCALE
        val originY = startY * SCALE
        val existing = IntArray(width * height)
        bitmap.getPixels(existing, 0, width, originX, originY, width, height)
        for (y in 0 until height) for (x in 0 until width) {
            val weight = maxOf(1, feather(x, width, left, right) * feather(y, height, top, bottom) / 32)
            val oldPixel = existing[y * width + x]
            val oldWeight = oldPixel ushr 24
            val sumWeight = (oldWeight + weight).coerceAtMost(255)
            val outputIndex = y * OUTPUT_SIDE + x
            val red = channel(output[outputIndex], oldPixel shr 16, oldWeight, weight)
            val green = channel(output[OUTPUT_PLANE + outputIndex], oldPixel shr 8, oldWeight, weight)
            val blue = channel(output[2 * OUTPUT_PLANE + outputIndex], oldPixel, oldWeight, weight)
            existing[y * width + x] = (sumWeight shl 24) or (red shl 16) or (green shl 8) or blue
        }
        bitmap.setPixels(existing, 0, width, originX, originY, width, height)
    }

    private fun channel(value: Float, previousPixelChannel: Int, oldWeight: Int, weight: Int): Int {
        val next = (value.coerceIn(0f, 1f) * 255f).toInt()
        if (oldWeight == 0) return next
        val previous = previousPixelChannel and 255
        return (previous * oldWeight + next * weight) / (oldWeight + weight)
    }

    private fun feather(position: Int, extent: Int, before: Boolean, after: Boolean): Int {
        val overlap = OVERLAP * SCALE
        val beforeWeight = if (before) ((position + 1).toFloat() / overlap).coerceIn(0f, 1f) else 1f
        val afterWeight = if (after) ((extent - position).toFloat() / overlap).coerceIn(0f, 1f) else 1f
        return (minOf(beforeWeight, afterWeight) * 32).toInt().coerceAtLeast(1)
    }

    private fun tileStarts(dimension: Int): List<Int> {
        if (dimension <= TILE) return listOf(0)
        val last = dimension - TILE
        val starts = mutableListOf(0)
        var current = 0
        while (current + TILE < dimension) {
            val next = current + TILE - OVERLAP
            if (next >= last) break
            current = next
            starts += current
        }
        if (starts.last() != last) starts += last
        return starts
    }

    fun cancel() { cancelled.set(true) }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        runCatching { runner.close() }
    }

    private class Runner(
        private val model: CompiledModel,
        private val inputs: List<TensorBuffer>,
        private val outputs: List<TensorBuffer>,
    ) : AutoCloseable {
        fun run(input: FloatArray): FloatArray {
            inputs[0].writeFloat(input)
            model.run(inputs, outputs)
            return outputs[0].readFloat()
        }

        override fun close() {
            inputs.forEach { it.close() }
            outputs.forEach { it.close() }
            model.close()
        }
    }
}
